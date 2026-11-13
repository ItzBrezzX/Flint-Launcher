package com.flintlauncher.android.ui.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flintlauncher.android.ui.theme.FlintColors
import kotlin.math.min

/**
 * Visual touchscreen layout editor.
 *
 * - Drag to move
 * - Slider for size & opacity
 * - Preset picker (does not overwrite custom without explicit save)
 * - Snap-to-grid optional
 * - Normalized coordinates for resolution independence
 *
 * Persistence is expected to go through the existing database / DataStore layer.
 */
@Composable
fun ControlEditorScreen(
    initial: ControlLayout = ControlPresets.defaultLayout(),
    onSave: (ControlLayout) -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf(initial) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var snap by remember { mutableStateOf(true) }
    var sizeSlider by remember { mutableFloatStateOf(0.12f) }
    var opacitySlider by remember { mutableFloatStateOf(0.75f) }

    val selected = layout.widgets.find { it.id == selectedId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FlintColors.Surface)
            .statusBarsPadding()
            .padding(12.dp),
    ) {
        Text("Control Layout Editor", style = MaterialTheme.typography.titleLarge, color = FlintColors.TextPrimary)
        Text("Drag widgets · adjust size/opacity · save layout", style = MaterialTheme.typography.bodySmall, color = FlintColors.TextMuted)

        // Preset chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            ControlPresets.all().take(4).forEach { preset ->
                FilterChip(
                    selected = layout.preset == preset.preset,
                    onClick = {
                        // Load preset into editor without auto-saving over custom
                        layout = preset
                        selectedId = null
                    },
                    label = { Text(preset.name) },
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                .border(1.dp, FlintColors.SurfaceVariant, RoundedCornerShape(12.dp)),
        ) {
            val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val minDim = min(w, h)

            // Grid
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (snap) {
                    val step = size.minDimension / 12f
                    var x = 0f
                    while (x < size.width) {
                        drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height), 1f)
                        x += step
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1f)
                        y += step
                    }
                }
            }

            layout.widgets.forEach { widget ->
                val px = widget.x * w
                val py = widget.y * h
                val diameter = widget.size * minDim
                val isSelected = widget.id == selectedId

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = with(LocalDensity.current) { (px - diameter / 2).coerceAtLeast(0f).toDp() },
                            top = with(LocalDensity.current) { (py - diameter / 2).coerceAtLeast(0f).toDp() },
                        )
                        .size(with(LocalDensity.current) { diameter.toDp() })
                        .background(
                            color = FlintColors.Ember.copy(alpha = widget.opacity * if (isSelected) 1f else 0.85f),
                            shape = if (widget.kind == ControlKind.JOYSTICK) CircleShape else RoundedCornerShape(8.dp),
                        )
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = Color.White,
                            shape = if (widget.kind == ControlKind.JOYSTICK) CircleShape else RoundedCornerShape(8.dp),
                        )
                        .pointerInput(widget.id, w, h, snap) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val nx = ((widget.x * w + dragAmount.x) / w).coerceIn(0.05f, 0.95f)
                                val ny = ((widget.y * h + dragAmount.y) / h).coerceIn(0.05f, 0.95f)
                                val finalX = if (snap) (nx * 12).toInt() / 12f else nx
                                val finalY = if (snap) (ny * 12).toInt() / 12f else ny
                                layout = layout.copy(
                                    widgets = layout.widgets.map {
                                        if (it.id == widget.id) it.copy(x = finalX, y = finalY) else it
                                    },
                                )
                                selectedId = widget.id
                                sizeSlider = widget.size
                                opacitySlider = widget.opacity
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = widget.label ?: widget.kind.name.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }

        // Property panel
        selected?.let { w ->
            Text("Selected: ${w.kind} (${w.id})", style = MaterialTheme.typography.labelLarge)
            Text("Size")
            Slider(
                value = sizeSlider,
                onValueChange = {
                    sizeSlider = it
                    layout = layout.copy(
                        widgets = layout.widgets.map { widget ->
                            if (widget.id == w.id) widget.copy(size = it) else widget
                        },
                    )
                },
                valueRange = 0.06f..0.30f,
            )
            Text("Opacity")
            Slider(
                value = opacitySlider,
                onValueChange = {
                    opacitySlider = it
                    layout = layout.copy(
                        widgets = layout.widgets.map { widget ->
                            if (widget.id == w.id) widget.copy(opacity = it) else widget
                        },
                    )
                },
                valueRange = 0.2f..1f,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = snap, onClick = { snap = !snap }, label = { Text(if (snap) "Snap ON" else "Snap OFF") })
            SpacerWeight()
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = {
                val toSave = layout.copy(
                    id = if (layout.preset != null) "custom-${System.currentTimeMillis()}" else layout.id,
                    name = if (layout.preset != null) "Custom" else layout.name,
                    preset = ControlPreset.CUSTOM.id,
                )
                onSave(toSave)
            }) { Text("Save Layout") }
        }
    }
}

@Composable
private fun SpacerWeight() {
    Box(modifier = Modifier.fillMaxWidth(0.01f))
}
