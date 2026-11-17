package com.flintlauncher.android

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.flintlauncher.android.nativebridge.FlintInputEvent
import com.flintlauncher.android.nativebridge.FlintNativeWindowBridge
import com.flintlauncher.android.nativebridge.NativeBridge
import org.json.JSONArray
import org.json.JSONObject

/**
 * Customizable Minecraft touch HUD.
 *
 * The default layout follows the reference layout: a compact top toolbar and a
 * 3x3 movement/action pad at the lower-left. The gear opens the control editor;
 * buttons can be added, rebound, removed (custom buttons), and dragged freely.
 * Layouts are persisted in app-private SharedPreferences using normalized
 * coordinates, so positions survive screen-size and orientation changes.
 */
class FlintTouchControlsOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private enum class Shape { PILL, TILE, CIRCLE }

    private data class TouchButton(
        val id: String,
        val label: String,
        val x: Float,
        val y: Float,
        val widthDp: Float,
        val heightDp: Float,
        val shape: Shape,
        val keyCode: Int? = null,
        val mouseButton: Int? = null,
        val action: String? = null,
        val custom: Boolean = false,
    )

    private data class KeyChoice(
        val label: String,
        val keyCode: Int? = null,
        val mouseButton: Int? = null,
        val action: String? = null,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private var buttons: MutableList<TouchButton> = loadButtons()
    private var editMode = false
    private var controlsVisible = true
    private var editingPointerId = MotionEvent.INVALID_POINTER_ID
    private var editingButtonId: String? = null
    private var editingOffsetX = 0f
    private var editingOffsetY = 0f
    private var mousePointerMode = false
    private var mousePointerActive = false
    private var mousePointerId = MotionEvent.INVALID_POINTER_ID
    private var mousePointerX = 0f
    private var mousePointerY = 0f

    private val activePointers = mutableMapOf<Int, TouchButton>()
    private val activePressCounts = mutableMapOf<String, Int>()

    /** Set by GameActivity so KEYBOARD targets the native game input connection. */
    var onKeyboardRequested: (() -> Unit)? = null

    private val keyChoices: List<KeyChoice> = buildList {
        for (code in 65..90) add(KeyChoice(code.toChar().toString(), keyCode = code))
        for (code in 48..57) add(KeyChoice(code.toChar().toString(), keyCode = code))
        addAll(
            listOf(
                KeyChoice("SPACE", 32),
                KeyChoice("ESC", 256),
                KeyChoice("ENTER", 257),
                KeyChoice("TAB", 258),
                KeyChoice("BACKSPACE", 259),
                KeyChoice("INSERT", 260),
                KeyChoice("DELETE", 261),
                KeyChoice("RIGHT", 262),
                KeyChoice("LEFT", 263),
                KeyChoice("DOWN", 264),
                KeyChoice("UP", 265),
                KeyChoice("F1", 290),
                KeyChoice("F2", 291),
                KeyChoice("F3", 292),
                KeyChoice("F4", 293),
                KeyChoice("F5", 294),
                KeyChoice("F6", 295),
                KeyChoice("F7", 296),
                KeyChoice("F8", 297),
                KeyChoice("F9", 298),
                KeyChoice("F10", 299),
                KeyChoice("F11", 300),
                KeyChoice("F12", 301),
                KeyChoice("SHIFT", 340),
                KeyChoice("CTRL", 341),
                KeyChoice("ALT", 342),
                KeyChoice("MOUSE LEFT", mouseButton = 0),
                KeyChoice("MOUSE RIGHT", mouseButton = 1),
                KeyChoice("ANDROID KEYBOARD", action = ACTION_KEYBOARD),
                KeyChoice("MOUSE MODE", action = ACTION_TOGGLE_MOUSE),
            ),
        )
    }

    init {
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = "Customizable Minecraft touch controls. Tap the gear to add, move, or rebind buttons."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        if (controlsVisible) {
            buttons.forEach { button ->
                drawButton(canvas, button)
            }
        } else {
            buttons.firstOrNull { it.action == ACTION_TOGGLE_MOUSE }?.let { drawButton(canvas, it) }
        }
        drawSettingsButton(canvas)
        if (mousePointerMode && mousePointerActive) drawMousePointer(canvas)

        if (editMode) {
            paint.style = Paint.Style.FILL
            paint.color = 0xdd101820.toInt()
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 11f * density
            canvas.drawText("MOVE MODE — drag buttons, tap gear when finished", width / 2f, height * 0.24f, paint)
        }
    }

    private fun drawButton(canvas: Canvas, button: TouchButton) {
        val bounds = buttonBounds(button)
        val isPressed = activePointers.values.any { it.id == button.id }
        paint.style = Paint.Style.FILL
        paint.color = if (isPressed) 0xb86f9eae.toInt() else 0x5f172634
        if (button.action == ACTION_TOGGLE_MOUSE && !controlsVisible) {
            paint.color = 0x9f35556a.toInt()
        }
        when (button.shape) {
            Shape.CIRCLE -> canvas.drawOval(bounds, paint)
            Shape.PILL -> canvas.drawRoundRect(bounds, 6f * density, 6f * density, paint)
            Shape.TILE -> canvas.drawRoundRect(bounds, 2f * density, 2f * density, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (editMode) 2.4f * density else 1.8f * density
        paint.color = if (editMode) 0xfff2c879.toInt() else 0xaac1d1da.toInt()
        when (button.shape) {
            Shape.CIRCLE -> canvas.drawOval(bounds, paint)
            Shape.PILL -> canvas.drawRoundRect(bounds, 6f * density, 6f * density, paint)
            Shape.TILE -> canvas.drawRoundRect(bounds, 2f * density, 2f * density, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = 0xffedf4f7.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val textSize = when {
            button.label.length >= 7 -> 8.5f
            button.label.length >= 5 -> 9.5f
            button.shape == Shape.TILE -> 11f
            else -> 10.5f
        } * density
        paint.textSize = textSize
        val baseline = bounds.centerY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(button.label, bounds.centerX(), baseline, paint)

        if (editMode) {
            paint.style = Paint.Style.FILL
            paint.color = 0xfff2c879.toInt()
            val handle = 3f * density
            canvas.drawCircle(bounds.left, bounds.top, handle, paint)
            canvas.drawCircle(bounds.right, bounds.top, handle, paint)
            canvas.drawCircle(bounds.left, bounds.bottom, handle, paint)
            canvas.drawCircle(bounds.right, bounds.bottom, handle, paint)
        }
    }

    private fun drawSettingsButton(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height * SETTINGS_Y
        val radius = 18f * density
        paint.style = Paint.Style.FILL
        paint.color = 0x9f263747.toInt()
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = 0xffc2d0d9.toInt()
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xffedf4f7.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 19f * density
        val baseline = centerY - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText("⚙", centerX, baseline, paint)
    }

    private fun drawMousePointer(canvas: Canvas) {
        val x = mousePointerX
        val y = mousePointerY
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = 0xff101820.toInt()
        canvas.drawCircle(x, y, 9f * density, paint)
        paint.color = 0xfff4f7f9.toInt()
        canvas.drawCircle(x, y, 7f * density, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xff101820.toInt()
        canvas.drawCircle(x, y, 2.5f * density, paint)
        paint.strokeWidth = 1.5f * density
        canvas.drawLine(x - 13f * density, y, x + 13f * density, y, paint)
        canvas.drawLine(x, y - 13f * density, x, y + 13f * density, paint)
    }

    private fun toggleMousePointerMode() {
        releaseAllControls()
        if (mousePointerMode) {
            releaseMousePointer()
            mousePointerMode = false
            mousePointerActive = false
            Toast.makeText(context, "Touch controls mode", Toast.LENGTH_SHORT).show()
        } else {
            mousePointerMode = true
            mousePointerActive = true
            if (mousePointerX == 0f && mousePointerY == 0f) {
                mousePointerX = width / 2f
                mousePointerY = height / 2f
            }
            enqueue(FlintInputEvent.CURSOR, x = mousePointerX.toDouble(), y = mousePointerY.toDouble())
            Toast.makeText(context, "Mouse pointer mode: drag to move, tap to click", Toast.LENGTH_SHORT).show()
        }
        invalidate()
    }

    private fun beginMousePointer(pointerId: Int, x: Float, y: Float) {
        mousePointerId = pointerId
        mousePointerActive = true
        mousePointerX = x.coerceIn(0f, width.toFloat())
        mousePointerY = y.coerceIn(0f, height.toFloat())
        enqueue(FlintInputEvent.CURSOR, x = mousePointerX.toDouble(), y = mousePointerY.toDouble())
        enqueue(FlintInputEvent.MOUSE_BUTTON, code = 0, action = 1,
            x = mousePointerX.toDouble(), y = mousePointerY.toDouble())
        invalidate()
    }

    private fun moveMousePointer(x: Float, y: Float) {
        mousePointerX = x.coerceIn(0f, width.toFloat())
        mousePointerY = y.coerceIn(0f, height.toFloat())
        enqueue(FlintInputEvent.CURSOR, x = mousePointerX.toDouble(), y = mousePointerY.toDouble())
        invalidate()
    }

    private fun releaseMousePointer() {
        if (mousePointerId != MotionEvent.INVALID_POINTER_ID && mousePointerActive) {
            enqueue(FlintInputEvent.MOUSE_BUTTON, code = 0, action = 0,
                x = mousePointerX.toDouble(), y = mousePointerY.toDouble())
        }
        mousePointerId = MotionEvent.INVALID_POINTER_ID
        // Keep the visible cursor at its last position while mouse mode remains enabled.
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerId = event.getPointerId(event.actionIndex)
                val x = event.getX(event.actionIndex)
                val y = event.getY(event.actionIndex)

                if (isSettingsHit(x, y)) {
                    performClick()
                    releaseAllControls()
                    showSettings()
                    return true
                }

                if (editMode) {
                    val target = findButtonAt(x, y)
                    editingPointerId = pointerId
                    editingButtonId = target?.id
                    if (target != null) {
                        val bounds = buttonBounds(target)
                        editingOffsetX = bounds.centerX() - x
                        editingOffsetY = bounds.centerY() - y
                    } else {
                        editingOffsetX = 0f
                        editingOffsetY = 0f
                    }
                    invalidate()
                    return true
                }

                val target = if (mousePointerMode) findMouseToggleAt(x, y) else findButtonAt(x, y)
                if (target?.action == ACTION_TOGGLE_MOUSE) {
                    toggleMousePointerMode()
                    return true
                }
                if (target == null && mousePointerMode) {
                    beginMousePointer(pointerId, x, y)
                    return true
                }
                if (target == null) return false
                if (target.action == ACTION_KEYBOARD) {
                    onKeyboardRequested?.invoke()
                    return true
                }

                pressButton(pointerId, target)
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = event.getPointerId(event.actionIndex)
                val x = event.getX(event.actionIndex)
                val y = event.getY(event.actionIndex)
                if (editMode) return true
                val target = if (mousePointerMode) findMouseToggleAt(x, y) else findButtonAt(x, y)
                if (target?.action == ACTION_TOGGLE_MOUSE) {
                    toggleMousePointerMode()
                    return true
                }
                if (target == null && mousePointerMode) {
                    enqueue(FlintInputEvent.CURSOR, x = x.toDouble(), y = y.toDouble())
                    return true
                }
                when (target?.action) {
                    ACTION_KEYBOARD -> {
                        onKeyboardRequested?.invoke()
                        return true
                    }
                    null -> if (target != null) pressButton(pointerId, target)
                }
                invalidate()
                return activePointers.isNotEmpty() || target != null
            }

            MotionEvent.ACTION_MOVE -> {
                if (mousePointerMode && mousePointerId != MotionEvent.INVALID_POINTER_ID) {
                    val index = event.findPointerIndex(mousePointerId)
                    if (index >= 0) moveMousePointer(event.getX(index), event.getY(index))
                    return true
                }
                if (editMode && editingPointerId != MotionEvent.INVALID_POINTER_ID) {
                    val index = event.findPointerIndex(editingPointerId)
                    if (index >= 0) {
                        editingButtonId?.let {
                            moveButtonTo(
                                it,
                                event.getX(index) + editingOffsetX,
                                event.getY(index) + editingOffsetY,
                            )
                        }
                        invalidate()
                    }
                    return true
                }
                return activePointers.isNotEmpty()
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                if (mousePointerMode && pointerId == mousePointerId) {
                    releaseMousePointer()
                    invalidate()
                    return true
                }
                if (editMode && pointerId == editingPointerId) {
                    editingPointerId = MotionEvent.INVALID_POINTER_ID
                    editingButtonId = null
                    editingOffsetX = 0f
                    editingOffsetY = 0f
                    saveButtons()
                    invalidate()
                    return true
                }
                releasePointer(pointerId)
                invalidate()
                return activePointers.isNotEmpty() || event.actionMasked == MotionEvent.ACTION_UP
            }

            MotionEvent.ACTION_CANCEL -> {
                releaseMousePointer()
                if (editMode) {
                    editingPointerId = MotionEvent.INVALID_POINTER_ID
                    editingButtonId = null
                    editingOffsetX = 0f
                    editingOffsetY = 0f
                    saveButtons()
                }
                releaseAllControls()
                invalidate()
                return true
            }
        }
        return activePointers.isNotEmpty() || editMode
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            releaseAllControls()
            releaseMousePointer()
            editingPointerId = MotionEvent.INVALID_POINTER_ID
            editingButtonId = null
            editingOffsetX = 0f
            editingOffsetY = 0f
            invalidate()
        }
    }

    private fun pressButton(pointerId: Int, button: TouchButton) {
        if (activePointers.containsKey(pointerId)) return
        activePointers[pointerId] = button
        val count = activePressCounts[button.id] ?: 0
        activePressCounts[button.id] = count + 1
        if (count == 0) sendBinding(button, pressed = true)
    }

    private fun releasePointer(pointerId: Int) {
        val button = activePointers.remove(pointerId) ?: return
        val count = (activePressCounts[button.id] ?: 1) - 1
        if (count <= 0) {
            activePressCounts.remove(button.id)
            sendBinding(button, pressed = false)
        } else {
            activePressCounts[button.id] = count
        }
    }

    private fun releaseAllControls() {
        activePointers.values.distinctBy { it.id }.forEach { sendBinding(it, pressed = false) }
        activePointers.clear()
        activePressCounts.clear()
    }

    private fun sendBinding(button: TouchButton, pressed: Boolean) {
        button.keyCode?.let { sendKey(it, pressed) }
        button.mouseButton?.let { mouseButton ->
            enqueue(
                FlintInputEvent.MOUSE_BUTTON,
                code = mouseButton,
                action = if (pressed) 1 else 0,
            )
        }
    }

    private fun sendKey(key: Int, pressed: Boolean) {
        enqueue(FlintInputEvent.KEY, code = key, action = if (pressed) 1 else 0)
    }

    private fun enqueue(
        type: Int,
        code: Int = 0,
        action: Int = 0,
        modifiers: Int = 0,
        x: Double = 0.0,
        y: Double = 0.0,
    ) {
        if (!NativeBridge.isNativeLoaded) return
        runCatching {
            FlintNativeWindowBridge.enqueue(
                type = type,
                code = code,
                action = action,
                modifiers = modifiers,
                x = x,
                y = y,
            )
        }.onFailure { android.util.Log.w(TAG, "Touch control event could not be queued", it) }
    }

    private fun buttonBounds(button: TouchButton): RectF {
        val centerX = button.x * width
        val centerY = button.y * height
        val halfWidth = button.widthDp * density / 2f
        val halfHeight = button.heightDp * density / 2f
        return RectF(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
    }

    private fun findButtonAt(x: Float, y: Float): TouchButton? {
        val candidates = if (controlsVisible) buttons else buttons.filter { it.action == ACTION_TOGGLE_MOUSE }
        return candidates.asReversed().firstOrNull { button ->
            val bounds = buttonBounds(button)
            val touchPadding = 5f * density
            bounds.inset(-touchPadding, -touchPadding)
            bounds.contains(x, y)
        }
    }

    private fun isSettingsHit(x: Float, y: Float): Boolean {
        val dx = x - width / 2f
        val dy = y - height * SETTINGS_Y
        val radius = 24f * density
        return dx * dx + dy * dy <= radius * radius
    }

    private fun findMouseToggleAt(x: Float, y: Float): TouchButton? =
        buttons.firstOrNull { button ->
            button.action == ACTION_TOGGLE_MOUSE && buttonBounds(button).contains(x, y)
        }

    private fun moveButtonTo(id: String, x: Float, y: Float) {
        val current = buttons.firstOrNull { it.id == id } ?: return
        val viewWidth = width.coerceAtLeast(1).toFloat()
        val viewHeight = height.coerceAtLeast(1).toFloat()
        val halfWidth = current.widthDp * density / 2f + 2f * density
        val halfHeight = current.heightDp * density / 2f + 2f * density
        val minX = (halfWidth / viewWidth).coerceAtMost(0.49f)
        val minY = (halfHeight / viewHeight).coerceAtMost(0.49f)
        val normalizedX = (x / viewWidth).coerceIn(minX, 1f - minX)
        val normalizedY = (y / viewHeight).coerceIn(minY, 1f - minY)
        buttons = buttons.map { button ->
            if (button.id == id) button.copy(x = normalizedX, y = normalizedY) else button
        }.toMutableList()
    }

    private fun showSettings() {
        val options = arrayOf(
            if (editMode) "Finish moving buttons" else "Move buttons freely",
            "Add a new button",
            "Choose a button and change its key",
            if (controlsVisible) "Hide touch controls" else "Show touch controls",
            "Reset to reference layout",
        )
        AlertDialog.Builder(context)
            .setTitle("Touch Controls Settings")
            .setItems(options) { _, selected ->
                when (selected) {
                    0 -> {
                        editMode = !editMode
                        controlsVisible = true
                        if (!editMode) {
                            editingPointerId = MotionEvent.INVALID_POINTER_ID
                            editingButtonId = null
                            saveButtons()
                        }
                        Toast.makeText(context, if (editMode) "Move mode enabled: drag any button" else "Move mode saved", Toast.LENGTH_SHORT).show()
                        invalidate()
                    }
                    1 -> showButtonEditor(existing = null)
                    2 -> showButtonManager()
                    3 -> {
                        controlsVisible = !controlsVisible
                        editMode = false
                        releaseAllControls()
                        invalidate()
                    }
                    4 -> {
                        releaseAllControls()
                        buttons = defaultButtons().toMutableList()
                        controlsVisible = true
                        editMode = false
                        saveButtons()
                        invalidate()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showButtonManager() {
        if (buttons.isEmpty()) {
            Toast.makeText(context, "No buttons are configured", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = buttons.map { button ->
            val binding = keyChoices.firstOrNull {
                it.keyCode == button.keyCode && it.mouseButton == button.mouseButton && it.action == button.action
            }?.label ?: when (button.action) {
                ACTION_KEYBOARD -> "Android keyboard"
                ACTION_TOGGLE_MOUSE -> "Mouse mode"
                else -> "No key"
            }
            "${button.label}  ·  $binding"
        }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Choose button to edit")
            .setItems(labels) { _, index -> showButtonEditor(buttons.getOrNull(index)) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showButtonEditor(existing: TouchButton?) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (18f * density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        val labelInput = EditText(context).apply {
            hint = "Button label"
            setSingleLine(true)
            setText(existing?.label ?: "NEW")
            selectAll()
        }
        val keyTitle = TextView(context).apply {
            text = "Key / mouse binding"
            textSize = 13f
            setPadding(0, (12f * density).toInt(), 0, (5f * density).toInt())
        }
        val spinner = Spinner(context)
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            keyChoices.map { it.label },
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.adapter = adapter
        val selectedChoice = keyChoices.indexOfFirst {
            it.keyCode == existing?.keyCode &&
                it.mouseButton == existing?.mouseButton &&
                it.action == existing?.action
        }
        if (selectedChoice >= 0) spinner.setSelection(selectedChoice)

        content.addView(labelInput)
        content.addView(keyTitle)
        content.addView(spinner)

        val builder = AlertDialog.Builder(context)
            .setTitle(if (existing == null) "Add custom button" else "Edit ${existing.label}")
            .setView(content)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
        if (existing?.custom == true) builder.setNeutralButton("Remove", null)

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val label = labelInput.text?.toString()?.trim().orEmpty().ifBlank { "BUTTON" }.take(10)
                val choice = keyChoices.getOrNull(spinner.selectedItemPosition) ?: keyChoices.first()
                if (existing == null) {
                    val added = TouchButton(
                        id = "custom-${System.currentTimeMillis()}",
                        label = label,
                        x = 0.50f,
                        y = 0.55f,
                        widthDp = 44f,
                        heightDp = 44f,
                        shape = Shape.CIRCLE,
                        keyCode = choice.keyCode,
                        mouseButton = choice.mouseButton,
                        action = choice.action,
                        custom = true,
                    )
                    buttons.add(added)
                    editMode = true
                    controlsVisible = true
                    Toast.makeText(context, "Button added. Drag it into position.", Toast.LENGTH_SHORT).show()
                } else {
                    buttons = buttons.map { button ->
                        if (button.id == existing.id) {
                            button.copy(
                                label = label,
                                keyCode = choice.keyCode,
                                mouseButton = choice.mouseButton,
                                action = choice.action,
                            )
                        } else button
                    }.toMutableList()
                }
                saveButtons()
                invalidate()
                dialog.dismiss()
            }
            if (existing?.custom == true) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    buttons.removeAll { it.id == existing.id }
                    releaseAllControls()
                    saveButtons()
                    invalidate()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun loadButtons(): MutableList<TouchButton> {
        val raw = preferences.getString(KEY_LAYOUT, null) ?: return defaultButtons().toMutableList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        TouchButton(
                            id = item.getString("id"),
                            label = item.getString("label"),
                            x = item.getDouble("x").toFloat().coerceIn(0.01f, 0.99f),
                            y = item.getDouble("y").toFloat().coerceIn(0.04f, 0.96f),
                            widthDp = item.getDouble("widthDp").toFloat().coerceIn(24f, 120f),
                            heightDp = item.getDouble("heightDp").toFloat().coerceIn(24f, 90f),
                            shape = runCatching { Shape.valueOf(item.optString("shape", Shape.CIRCLE.name)) }.getOrDefault(Shape.CIRCLE),
                            keyCode = if (item.isNull("keyCode")) null else item.optInt("keyCode"),
                            mouseButton = if (item.isNull("mouseButton")) null else item.optInt("mouseButton"),
                            action = item.optString("action").takeIf { it.isNotBlank() && it != "null" },
                            custom = item.optBoolean("custom", false),
                        ),
                    )
                }
            }.toMutableList().ifEmpty { defaultButtons().toMutableList() }
        }.getOrElse {
            android.util.Log.w(TAG, "Saved touch layout was invalid; using defaults", it)
            defaultButtons().toMutableList()
        }
    }

    private fun saveButtons() {
        val array = JSONArray()
        buttons.forEach { button ->
            array.put(
                JSONObject().apply {
                    put("id", button.id)
                    put("label", button.label)
                    put("x", button.x.toDouble())
                    put("y", button.y.toDouble())
                    put("widthDp", button.widthDp.toDouble())
                    put("heightDp", button.heightDp.toDouble())
                    put("shape", button.shape.name)
                    put("keyCode", button.keyCode ?: JSONObject.NULL)
                    put("mouseButton", button.mouseButton ?: JSONObject.NULL)
                    put("action", button.action ?: JSONObject.NULL)
                    put("custom", button.custom)
                },
            )
        }
        preferences.edit().putString(KEY_LAYOUT, array.toString()).apply()
    }

    private fun defaultButtons(): List<TouchButton> = listOf(
        // Top toolbar.
        TouchButton("debug", "DEBUG", 0.127f, 0.150f, 64f, 32f, Shape.PILL, keyCode = 292),
        TouchButton("chat", "CHAT", 0.215f, 0.150f, 62f, 32f, Shape.PILL, keyCode = 84),
        TouchButton("keyboard", "KEYBOARD", 0.305f, 0.150f, 76f, 32f, Shape.PILL, action = ACTION_KEYBOARD),
        TouchButton("tab", "TAB", 0.397f, 0.150f, 62f, 32f, Shape.PILL, keyCode = 258),
        TouchButton("mouse", "MOUSE", 0.875f, 0.150f, 72f, 32f, Shape.PILL, action = ACTION_TOGGLE_MOUSE),
        TouchButton("third-person", "3RD", 0.127f, 0.235f, 64f, 32f, Shape.PILL, keyCode = 294),

        // 3x3 pad: primary / movement / secondary, GUI, jump, inventory.
        TouchButton("primary", "PRI", 0.112f, 0.700f, 39f, 39f, Shape.TILE, mouseButton = 0),
        TouchButton("up", "▲", 0.163f, 0.700f, 39f, 39f, Shape.TILE, keyCode = 87),
        TouchButton("secondary", "SEC", 0.214f, 0.700f, 39f, 39f, Shape.TILE, mouseButton = 1),
        TouchButton("left", "◀", 0.112f, 0.785f, 39f, 39f, Shape.TILE, keyCode = 65),
        TouchButton("jump", "◇", 0.163f, 0.785f, 39f, 39f, Shape.TILE, keyCode = 32),
        TouchButton("right", "▶", 0.214f, 0.785f, 39f, 39f, Shape.TILE, keyCode = 68),
        TouchButton("gui", "GUI", 0.112f, 0.870f, 39f, 39f, Shape.TILE, keyCode = 290),
        TouchButton("down", "▼", 0.163f, 0.870f, 39f, 39f, Shape.TILE, keyCode = 83),
        TouchButton("inventory", "INV", 0.214f, 0.870f, 39f, 39f, Shape.TILE, keyCode = 69),
    )

    private companion object {
        const val TAG = "FlintTouchControls"
        const val PREFERENCES = "flint_touch_controls"
        const val KEY_LAYOUT = "custom_layout_v1"
        const val ACTION_KEYBOARD = "show_keyboard"
        const val ACTION_TOGGLE_MOUSE = "toggle_mouse"
        const val SETTINGS_Y = 0.130f
    }
}
