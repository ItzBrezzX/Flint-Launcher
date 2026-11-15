package com.flintlauncher.android.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import com.flintlauncher.android.nativebridge.GlfwBridge
import com.flintlauncher.android.nativebridge.GlfwKeys
import kotlin.math.hypot
import kotlin.math.min

/**
 * Touch controls drawn over the game surface (fixed layout for now; the layout editor is separate).
 *
 * While the cursor is hidden (in the world) the screen behaves like a game pad: left stick walks,
 * dragging elsewhere looks around, buttons jump/sneak/attack/use. While the cursor is visible
 * (menus, inventory) the whole screen is a mouse: touch = move + left button.
 */
class GameControlsView(
    context: Context,
    private val onToggleKeyboard: () -> Unit,
    private val onQuitRequested: () -> Unit,
) : View(context) {

    private sealed interface Action {
        data class Key(val glfwKey: Int, val hold: Boolean) : Action
        data class Mouse(val button: Int) : Action
        data class Scroll(val amount: Double) : Action
        data object Keyboard : Action
        data object Quit : Action
    }

    private class Control(val label: String, val gameplayOnly: Boolean, val action: Action) {
        val rect = RectF()
        var pressed = false
    }

    private sealed interface Role
    private data object StickRole : Role
    private class ControlRole(val control: Control) : Role
    private class LookRole(var lastX: Float, var lastY: Float, val downX: Float, val downY: Float, val downTime: Long) : Role {
        var moved = false
        var holding = false
        var longPress: Runnable? = null
    }

    private val controls = listOf(
        Control("ESC", false, Action.Key(GlfwKeys.KEY_ESCAPE, hold = false)),
        Control("INV", false, Action.Key(GlfwKeys.KEY_E, hold = false)),
        Control("CHAT", false, Action.Key(GlfwKeys.KEY_T, hold = false)),
        Control("KB", false, Action.Keyboard),
        Control("F5", true, Action.Key(GlfwKeys.KEY_F5, hold = false)),
        Control("DROP", true, Action.Key(GlfwKeys.KEY_Q, hold = false)),
        Control("JUMP", true, Action.Key(GlfwKeys.KEY_SPACE, hold = true)),
        Control("SNEAK", true, Action.Key(GlfwKeys.KEY_LEFT_SHIFT, hold = true)),
        Control("USE", true, Action.Mouse(GlfwKeys.MOUSE_RIGHT)),
        Control("HIT", true, Action.Mouse(GlfwKeys.MOUSE_LEFT)),
        Control("<", true, Action.Scroll(1.0)),
        Control(">", true, Action.Scroll(-1.0)),
        Control("X", false, Action.Quit),
    )

    private val roles = SparseArray<Role>()
    private val stickZone = RectF()
    private val stickCenter = floatArrayOf(0f, 0f)
    private var stickRadius = 1f
    private var stickX = 0f
    private var stickY = 0f
    private val stickKeysDown = HashSet<Int>()

    private val density = resources.displayMetrics.density
    private val slop = 10f * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val fillPressed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 120, 255, 160) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val ticker = object : Runnable {
        override fun run() {
            invalidate() // the cursor-grab state changes without any touch, so redraw regularly
            postDelayed(this, 250)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    private fun grabbed(): Boolean = GlfwBridge.isLoaded && GlfwBridge.nativeIsCursorGrabbed()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val u = min(w, h) / 10f
        fun place(label: String, left: Float, top: Float, width: Float, height: Float) {
            controls.first { it.label == label }.rect.set(left, top, left + width, top + height)
        }
        // Always available (menus too)
        place("ESC", 0.3f * u, 0.3f * u, 1.6f * u, 1.0f * u)
        place("INV", 2.1f * u, 0.3f * u, 1.6f * u, 1.0f * u)
        place("CHAT", 3.9f * u, 0.3f * u, 1.6f * u, 1.0f * u)
        place("KB", 5.7f * u, 0.3f * u, 1.4f * u, 1.0f * u)
        place("X", w - 1.5f * u, 0.3f * u, 1.2f * u, 1.0f * u)
        // In-world only
        place("F5", 7.3f * u, 0.3f * u, 1.4f * u, 1.0f * u)
        place("DROP", w - 3.2f * u, 1.6f * u, 1.6f * u, 1.0f * u)
        place("JUMP", w - 2.9f * u, h - 3.0f * u, 2.5f * u, 1.6f * u)
        place("SNEAK", w - 5.7f * u, h - 1.9f * u, 2.6f * u, 1.5f * u)
        place("USE", w - 2.9f * u, h - 5.0f * u, 2.5f * u, 1.6f * u)
        place("HIT", w - 5.7f * u, h - 3.9f * u, 2.6f * u, 1.6f * u)
        place("<", w / 2f - 3.2f * u, h - 1.5f * u, 1.4f * u, 1.1f * u)
        place(">", w / 2f + 1.8f * u, h - 1.5f * u, 1.4f * u, 1.1f * u)

        stickRadius = 1.7f * u
        stickCenter[0] = 2.4f * u
        stickCenter[1] = h - 2.6f * u
        stickZone.set(0f, h * 0.35f, w * 0.38f, h.toFloat())
    }

    // ----------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        val inWorld = grabbed()
        text.textSize = 13f * density
        for (control in controls) {
            if (control.gameplayOnly && !inWorld) continue
            canvas.drawRoundRect(control.rect, 14f * density, 14f * density, if (control.pressed) fillPressed else fill)
            canvas.drawRoundRect(control.rect, 14f * density, 14f * density, stroke)
            canvas.drawText(control.label, control.rect.centerX(), control.rect.centerY() + text.textSize / 3f, text)
        }
        if (inWorld) {
            canvas.drawCircle(stickCenter[0], stickCenter[1], stickRadius, fill)
            canvas.drawCircle(stickCenter[0], stickCenter[1], stickRadius, stroke)
            canvas.drawCircle(stickCenter[0] + stickX, stickCenter[1] + stickY, stickRadius * 0.45f, fillPressed)
        }
    }

    // ------------------------------------------------------------------- touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!GlfwBridge.isLoaded) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                pointerDown(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                pointerMove(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                pointerUp(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> releaseEverything()
        }
        invalidate()
        return true
    }

    private fun pointerDown(id: Int, x: Float, y: Float) {
        val inWorld = grabbed()
        val control = controls.firstOrNull { (inWorld || !it.gameplayOnly) && it.rect.contains(x, y) }
        if (control != null) {
            roles.put(id, ControlRole(control))
            press(control)
            return
        }
        if (inWorld && stickZone.contains(x, y)) {
            roles.put(id, StickRole)
            moveStick(x, y)
            return
        }
        val look = LookRole(x, y, x, y, SystemClock.uptimeMillis())
        roles.put(id, look)
        if (inWorld) {
            // Holding still for a moment starts "attack/break" (left button held).
            val hold = Runnable {
                if (!look.moved && roles.get(id) === look) {
                    look.holding = true
                    GlfwBridge.nativeSendMouseButton(GlfwKeys.MOUSE_LEFT, GlfwKeys.PRESS, 0)
                }
            }
            look.longPress = hold
            postDelayed(hold, LONG_PRESS_MS)
        } else {
            GlfwBridge.nativeSendCursorPos(x.toDouble(), y.toDouble())
            GlfwBridge.nativeSendMouseButton(GlfwKeys.MOUSE_LEFT, GlfwKeys.PRESS, 0)
            look.holding = true
        }
    }

    private fun pointerMove(id: Int, x: Float, y: Float) {
        when (val role = roles.get(id)) {
            is StickRole -> moveStick(x, y)
            is LookRole -> {
                if (grabbed()) {
                    GlfwBridge.nativeSendCursorDelta((x - role.lastX).toDouble(), (y - role.lastY).toDouble())
                } else {
                    GlfwBridge.nativeSendCursorPos(x.toDouble(), y.toDouble())
                }
                if (hypot(x - role.downX, y - role.downY) > slop) {
                    role.moved = true
                    role.longPress?.let { removeCallbacks(it) }
                }
                role.lastX = x
                role.lastY = y
            }
            else -> Unit
        }
    }

    private fun pointerUp(id: Int, x: Float, y: Float) {
        when (val role = roles.get(id)) {
            is ControlRole -> release(role.control)
            is StickRole -> resetStick()
            is LookRole -> {
                role.longPress?.let { removeCallbacks(it) }
                if (role.holding) {
                    if (!grabbed()) GlfwBridge.nativeSendCursorPos(x.toDouble(), y.toDouble())
                    GlfwBridge.nativeSendMouseButton(GlfwKeys.MOUSE_LEFT, GlfwKeys.RELEASE, 0)
                } else if (grabbed() && !role.moved && SystemClock.uptimeMillis() - role.downTime < LONG_PRESS_MS) {
                    // A quick tap in the world is one swing / one click.
                    GlfwBridge.nativeSendMouseButton(GlfwKeys.MOUSE_LEFT, GlfwKeys.PRESS, 0)
                    GlfwBridge.nativeSendMouseButton(GlfwKeys.MOUSE_LEFT, GlfwKeys.RELEASE, 0)
                }
            }
            else -> Unit
        }
        roles.remove(id)
    }

    private fun releaseEverything() {
        for (i in 0 until roles.size()) {
            when (val role = roles.valueAt(i)) {
                is ControlRole -> release(role.control)
                is StickRole -> resetStick()
                is LookRole -> {
                    role.longPress?.let { removeCallbacks(it) }
                    if (role.holding) GlfwBridge.nativeSendMouseButton(GlfwKeys.MOUSE_LEFT, GlfwKeys.RELEASE, 0)
                }
                else -> Unit
            }
        }
        roles.clear()
    }

    // ---------------------------------------------------------------- controls

    private fun press(control: Control) {
        control.pressed = true
        when (val action = control.action) {
            is Action.Key -> if (action.hold) GameInput.key(action.glfwKey, GlfwKeys.PRESS) else GameInput.tap(action.glfwKey)
            is Action.Mouse -> GlfwBridge.nativeSendMouseButton(action.button, GlfwKeys.PRESS, 0)
            is Action.Scroll -> GlfwBridge.nativeSendScroll(0.0, action.amount)
            Action.Keyboard -> onToggleKeyboard()
            Action.Quit -> onQuitRequested()
        }
    }

    private fun release(control: Control) {
        control.pressed = false
        when (val action = control.action) {
            is Action.Key -> if (action.hold) GameInput.key(action.glfwKey, GlfwKeys.RELEASE)
            is Action.Mouse -> GlfwBridge.nativeSendMouseButton(action.button, GlfwKeys.RELEASE, 0)
            else -> Unit
        }
    }

    private fun moveStick(x: Float, y: Float) {
        var dx = x - stickCenter[0]
        var dy = y - stickCenter[1]
        val length = hypot(dx, dy)
        if (length > stickRadius) {
            dx = dx / length * stickRadius
            dy = dy / length * stickRadius
        }
        stickX = dx
        stickY = dy
        val nx = dx / stickRadius
        val ny = dy / stickRadius
        setStickKey(GlfwKeys.KEY_W, ny < -STICK_THRESHOLD)
        setStickKey(GlfwKeys.KEY_S, ny > STICK_THRESHOLD)
        setStickKey(GlfwKeys.KEY_A, nx < -STICK_THRESHOLD)
        setStickKey(GlfwKeys.KEY_D, nx > STICK_THRESHOLD)
        setStickKey(GlfwKeys.KEY_LEFT_CONTROL, ny < -SPRINT_THRESHOLD) // push the stick all the way: sprint
    }

    private fun resetStick() {
        stickX = 0f
        stickY = 0f
        for (key in stickKeysDown.toList()) setStickKey(key, false)
    }

    private fun setStickKey(key: Int, down: Boolean) {
        if (down && stickKeysDown.add(key)) GameInput.key(key, GlfwKeys.PRESS)
        else if (!down && stickKeysDown.remove(key)) GameInput.key(key, GlfwKeys.RELEASE)
    }

    private companion object {
        const val LONG_PRESS_MS = 350L
        const val STICK_THRESHOLD = 0.35f
        const val SPRINT_THRESHOLD = 0.9f
    }
}
