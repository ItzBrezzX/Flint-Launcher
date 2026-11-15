package com.flintlauncher.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.util.Log
import com.flintlauncher.android.nativebridge.FlintInputEvent
import com.flintlauncher.android.nativebridge.FlintNativeWindowBridge
import com.flintlauncher.android.nativebridge.NativeBridge

/**
 * Optional on-screen Minecraft controls layered over the game Surface.
 *
 * The left virtual stick sends continuous WASD transitions. The right controls
 * send paired GLFW key/mouse press-release events. Touches outside control zones
 * fall through to FlintGameSurfaceView so the user can still look around.
 */
class FlintTouchControlsOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private enum class Control(
        val label: String,
        val key: Int? = null,
        val mouseButton: Int? = null,
    ) {
        ATTACK("ATK", mouseButton = 0),
        USE("USE", mouseButton = 1),
        JUMP("JUMP", key = 32),
        SNEAK("SHIFT", key = 340),
        INVENTORY("INV", key = 69),
        PAUSE("ESC", key = 256),
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeButtons = mutableMapOf<Int, Control>()
    private var joystickPointer = MotionEvent.INVALID_POINTER_ID
    private var joystickX = 0f
    private var joystickY = 0f
    private var pressedJoystickKeys: Set<Int> = emptySet()
    private val density = resources.displayMetrics.density

    init {
        isClickable = true
        contentDescription = "Minecraft touch controls: movement joystick, attack, use, jump, sneak, inventory and pause"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val radius = buttonRadius()
        val stickCenterX = 1.55f * radius
        val stickCenterY = height - 1.75f * radius
        val stickRadius = 1.35f * radius

        paint.style = Paint.Style.FILL
        paint.color = 0x3f0b1722
        canvas.drawCircle(stickCenterX, stickCenterY, stickRadius, paint)
        paint.color = 0x805a7184.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        canvas.drawCircle(stickCenterX, stickCenterY, stickRadius, paint)

        val knobX = if (joystickPointer == MotionEvent.INVALID_POINTER_ID) stickCenterX else joystickX
        val knobY = if (joystickPointer == MotionEvent.INVALID_POINTER_ID) stickCenterY else joystickY
        paint.style = Paint.Style.FILL
        paint.color = 0x997c9bad.toInt()
        canvas.drawCircle(knobX, knobY, 0.58f * radius, paint)

        for (control in Control.entries) {
            val point = buttonCenter(control, radius)
            val down = activeButtons.values.any { it == control }
            paint.style = Paint.Style.FILL
            paint.color = if (down) 0xb86f9eae.toInt() else 0x5f172634
            canvas.drawCircle(point.first, point.second, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = 0xaac1d1da.toInt()
            canvas.drawCircle(point.first, point.second, radius, paint)
            paint.style = Paint.Style.FILL
            paint.color = 0xffedf4f7.toInt()
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = if (control.label.length > 4) 10f * density else 12f * density
            canvas.drawText(control.label, point.first, point.second + paint.textSize * 0.35f, paint)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            releaseAllControls()
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointer = event.actionIndex
                if (beginControl(event.getPointerId(pointer), event.getX(pointer), event.getY(pointer))) {
                    invalidate()
                    return true
                }
                return false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointer = event.actionIndex
                beginControl(event.getPointerId(pointer), event.getX(pointer), event.getY(pointer))
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val index = if (joystickPointer == MotionEvent.INVALID_POINTER_ID) -1
                    else event.findPointerIndex(joystickPointer)
                if (index >= 0) moveJoystick(event.getX(index), event.getY(index))
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                endControl(pointerId)
                invalidate()
                return activeButtons.isNotEmpty() || joystickPointer != MotionEvent.INVALID_POINTER_ID || event.actionMasked == MotionEvent.ACTION_UP
            }

            MotionEvent.ACTION_CANCEL -> {
                releaseAllControls()
                invalidate()
                return true
            }
        }
        return activeButtons.isNotEmpty() || joystickPointer != MotionEvent.INVALID_POINTER_ID
    }

    private fun beginControl(pointerId: Int, x: Float, y: Float): Boolean {
        if (pointerId == joystickPointer || activeButtons.containsKey(pointerId)) return true
        val radius = buttonRadius()
        val stickX = 1.55f * radius
        val stickY = height - 1.75f * radius
        val stickRadius = 1.35f * radius

        if (joystickPointer == MotionEvent.INVALID_POINTER_ID &&
            distanceSquared(x, y, stickX, stickY) <= stickRadius * stickRadius
        ) {
            joystickPointer = pointerId
            moveJoystick(x, y)
            return true
        }

        val control = Control.entries.firstOrNull { candidate ->
            val point = buttonCenter(candidate, radius)
            distanceSquared(x, y, point.first, point.second) <= radius * radius
        } ?: return false

        val alreadyPressed = activeButtons.values.any { it == control }
        activeButtons[pointerId] = control
        if (!alreadyPressed) sendControl(control, pressed = true)
        return true
    }

    private fun moveJoystick(x: Float, y: Float) {
        val radius = buttonRadius()
        val centerX = 1.55f * radius
        val centerY = height - 1.75f * radius
        val maxTravel = 0.85f * radius
        var dx = (x - centerX) / maxTravel
        var dy = (y - centerY) / maxTravel
        val magnitude = kotlin.math.sqrt(dx * dx + dy * dy)
        if (magnitude > 1f) {
            dx /= magnitude
            dy /= magnitude
        }
        joystickX = centerX + dx * maxTravel
        joystickY = centerY + dy * maxTravel

        val nextKeys = FlintTouchInputMapper.keysForVector(dx, dy)
        (pressedJoystickKeys - nextKeys).forEach { sendKey(it, pressed = false) }
        (nextKeys - pressedJoystickKeys).forEach { sendKey(it, pressed = true) }
        pressedJoystickKeys = nextKeys
    }

    private fun endControl(pointerId: Int) {
        if (pointerId == joystickPointer) {
            pressedJoystickKeys.forEach { sendKey(it, pressed = false) }
            pressedJoystickKeys = emptySet()
            joystickPointer = MotionEvent.INVALID_POINTER_ID
            return
        }
        activeButtons.remove(pointerId)?.let { control ->
            if (activeButtons.values.none { it == control }) {
                sendControl(control, pressed = false)
            }
        }
    }

    private fun releaseAllControls() {
        pressedJoystickKeys.forEach { sendKey(it, pressed = false) }
        pressedJoystickKeys = emptySet()
        activeButtons.values.toSet().forEach { sendControl(it, pressed = false) }
        activeButtons.clear()
        joystickPointer = MotionEvent.INVALID_POINTER_ID
    }

    private fun sendControl(control: Control, pressed: Boolean) {
        control.key?.let { sendKey(it, pressed) }
        control.mouseButton?.let { button ->
            enqueue(
                FlintInputEvent.MOUSE_BUTTON,
                code = button,
                action = if (pressed) 1 else 0,
            )
        }
    }

    private fun sendKey(key: Int, pressed: Boolean) {
        enqueue(FlintInputEvent.KEY, code = key, action = if (pressed) 1 else 0)
    }

    private fun enqueue(type: Int, code: Int = 0, action: Int = 0) {
        if (!NativeBridge.isNativeLoaded) return
        runCatching {
            FlintNativeWindowBridge.enqueue(type = type, code = code, action = action)
        }.onFailure { Log.w(TAG, "Touch control event could not be queued", it) }
    }

    private fun buttonCenter(control: Control, radius: Float): Pair<Float, Float> {
        val right = width - 1.55f * radius
        val inner = width - 3.35f * radius
        val top = height - 6.0f * radius
        val lower = height - 3.65f * radius
        return when (control) {
            Control.ATTACK -> right to lower
            Control.USE -> inner to lower
            Control.JUMP -> right to top
            Control.SNEAK -> inner to top
            Control.INVENTORY -> (width - 5.15f * radius) to lower
            Control.PAUSE -> (width - 1.2f * radius) to (1.35f * radius)
        }
    }

    private fun buttonRadius(): Float = 27f * density

    private fun distanceSquared(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    private companion object {
        const val TAG = "FlintTouchControls"
    }
}
