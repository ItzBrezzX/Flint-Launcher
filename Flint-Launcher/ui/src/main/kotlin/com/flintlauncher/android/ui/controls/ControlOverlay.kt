package com.flintlauncher.android.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flintlauncher.android.ui.theme.FlintColors
import kotlin.math.hypot
import kotlin.math.min

/**
 * In-game control overlay.
 *
 * Renders above the game surface and emits [ControlEvent]s through [listener].
 * Until a real Minecraft runtime / input bridge exists, events are delivered
 * but cannot control a running game process — mark integration as NOT VERIFIED.
 */
@Composable
fun ControlOverlay(
    layout: ControlLayout,
    listener: ControlInputListener,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    if (!visible) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val minDim = min(w, h)

        layout.widgets.forEach { widget ->
            when (widget.kind) {
                ControlKind.JOYSTICK -> JoystickWidget(widget, w, h, minDim, listener)
                else -> ButtonWidget(widget, w, h, minDim, listener)
            }
        }
    }
}

@Composable
private fun JoystickWidget(
    widget: ControlWidget,
    screenW: Float,
    screenH: Float,
    minDim: Float,
    listener: ControlInputListener,
) {
    val diameter = widget.size * minDim
    val cx = widget.x * screenW
    val cy = widget.y * screenH
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val maxTravel = diameter * 0.35f

    Box(
        modifier = Modifier
            .padding(
                start = with(LocalDensity.current) { (cx - diameter / 2).coerceAtLeast(0f).toDp() },
                top = with(LocalDensity.current) { (cy - diameter / 2).coerceAtLeast(0f).toDp() },
            )
            .size(with(LocalDensity.current) { diameter.toDp() })
            .background(FlintColors.Ember.copy(alpha = widget.opacity * 0.45f), CircleShape)
            .pointerInput(widget.id) {
                detectDragGestures(
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        listener.onControlEvent(ControlEvent.Joystick(widget.id, 0f, 0f))
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        listener.onControlEvent(ControlEvent.Joystick(widget.id, 0f, 0f))
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val next = knobOffset + dragAmount
                        val len = hypot(next.x, next.y)
                        val clamped = if (len > maxTravel && len > 0f) next * (maxTravel / len) else next
                        knobOffset = clamped
                        val nx = (clamped.x / maxTravel).coerceIn(-1f, 1f)
                        val ny = (clamped.y / maxTravel).coerceIn(-1f, 1f)
                        listener.onControlEvent(ControlEvent.Joystick(widget.id, nx, ny))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(
                    start = with(LocalDensity.current) { (knobOffset.x + diameter / 2 - diameter * 0.18f).toDp() },
                    top = with(LocalDensity.current) { (knobOffset.y + diameter / 2 - diameter * 0.18f).toDp() },
                )
                .size(with(LocalDensity.current) { (diameter * 0.36f).toDp() })
                .background(Color.White.copy(alpha = 0.85f), CircleShape),
        )
    }
}

@Composable
private fun ButtonWidget(
    widget: ControlWidget,
    screenW: Float,
    screenH: Float,
    minDim: Float,
    listener: ControlInputListener,
) {
    val diameter = widget.size * minDim
    val cx = widget.x * screenW
    val cy = widget.y * screenH
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(
                start = with(LocalDensity.current) { (cx - diameter / 2).coerceAtLeast(0f).toDp() },
                top = with(LocalDensity.current) { (cy - diameter / 2).coerceAtLeast(0f).toDp() },
            )
            .size(with(LocalDensity.current) { diameter.toDp() })
            .background(
                color = FlintColors.Ember.copy(alpha = widget.opacity * if (pressed) 1f else 0.75f),
                shape = RoundedCornerShape(10.dp),
            )
            .pointerInput(widget.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        listener.onControlEvent(ControlEvent.ButtonDown(widget.id, widget.kind))
                        try {
                            awaitRelease()
                        } finally {
                            pressed = false
                            listener.onControlEvent(ControlEvent.ButtonUp(widget.id, widget.kind))
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = widget.label ?: widget.kind.name.take(3),
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
    }
}
