package com.flintlauncher.android.ui.controls

import kotlinx.serialization.Serializable

/**
 * Normalized control layout (0..1 coordinates relative to screen width/height).
 * Inspired by Horizon Android / Starling touch control editor concepts:
 * - movable / resizable widgets
 * - opacity
 * - presets
 * - persistence
 *
 * Input events produced by the overlay are delivered through [ControlInputListener]
 * so the future Java runtime / JNI bridge can consume them. Until the runtime
 * exists, events are recorded for testing only (NOT VERIFIED against real Minecraft).
 */
@Serializable
enum class ControlKind {
    JOYSTICK,
    DPAD,
    JUMP,
    SNEAK,
    SPRINT,
    ATTACK,
    USE,
    INVENTORY,
    CHAT,
    PERSPECTIVE,
    DROP,
    HOTBAR_LEFT,
    HOTBAR_RIGHT,
    PAUSE,
    CUSTOM,
}

@Serializable
data class ControlWidget(
    val id: String,
    val kind: ControlKind,
    /** Center X in 0..1 of screen width. */
    val x: Float,
    /** Center Y in 0..1 of screen height. */
    val y: Float,
    /** Diameter / side length as fraction of min(screenW, screenH). */
    val size: Float,
    val opacity: Float = 0.75f,
    val label: String? = null,
    /** For CUSTOM: optional keycode or action name. */
    val action: String? = null,
)

@Serializable
data class ControlLayout(
    val id: String,
    val name: String,
    val preset: String? = null,
    val widgets: List<ControlWidget>,
    val version: Int = 1,
)

enum class ControlPreset(val id: String, val displayName: String) {
    DEFAULT("default", "Default"),
    COMPACT("compact", "Compact"),
    SURVIVAL("survival", "Survival"),
    CREATIVE("creative", "Creative"),
    PVP("pvp", "PvP"),
    LEFT_HANDED("left_handed", "Left-handed"),
    RIGHT_HANDED("right_handed", "Right-handed"),
    CUSTOM("custom", "Custom"),
}

/** Events the overlay emits. Runtime must map these to Minecraft input. */
sealed interface ControlEvent {
    data class Joystick(val id: String, val nx: Float, val ny: Float) : ControlEvent
    data class ButtonDown(val id: String, val kind: ControlKind) : ControlEvent
    data class ButtonUp(val id: String, val kind: ControlKind) : ControlEvent
    data class Dpad(val id: String, val dx: Int, val dy: Int) : ControlEvent
}

fun interface ControlInputListener {
    fun onControlEvent(event: ControlEvent)
}

object ControlPresets {
    fun defaultLayout(name: String = "Default"): ControlLayout = ControlLayout(
        id = "preset-default",
        name = name,
        preset = ControlPreset.DEFAULT.id,
        widgets = listOf(
            ControlWidget("joy", ControlKind.JOYSTICK, 0.18f, 0.72f, 0.22f, 0.7f),
            ControlWidget("jump", ControlKind.JUMP, 0.82f, 0.78f, 0.12f, 0.8f, "Jump"),
            ControlWidget("sneak", ControlKind.SNEAK, 0.70f, 0.85f, 0.10f, 0.75f, "Sneak"),
            ControlWidget("sprint", ControlKind.SPRINT, 0.88f, 0.65f, 0.10f, 0.75f, "Sprint"),
            ControlWidget("attack", ControlKind.ATTACK, 0.78f, 0.55f, 0.11f, 0.8f, "Attack"),
            ControlWidget("use", ControlKind.USE, 0.90f, 0.48f, 0.10f, 0.8f, "Use"),
            ControlWidget("inv", ControlKind.INVENTORY, 0.50f, 0.92f, 0.09f, 0.7f, "Inv"),
            ControlWidget("chat", ControlKind.CHAT, 0.08f, 0.08f, 0.08f, 0.65f, "Chat"),
            ControlWidget("pause", ControlKind.PAUSE, 0.92f, 0.08f, 0.08f, 0.65f, "Pause"),
            ControlWidget("drop", ControlKind.DROP, 0.62f, 0.78f, 0.09f, 0.7f, "Drop"),
        ),
    )

    fun compact(): ControlLayout = defaultLayout("Compact").copy(
        id = "preset-compact",
        preset = ControlPreset.COMPACT.id,
        widgets = defaultLayout().widgets.map {
            it.copy(size = it.size * 0.85f, opacity = 0.65f)
        },
    )

    fun pvp(): ControlLayout = defaultLayout("PvP").copy(
        id = "preset-pvp",
        preset = ControlPreset.PVP.id,
        widgets = defaultLayout().widgets.map { w ->
            when (w.kind) {
                ControlKind.ATTACK, ControlKind.USE -> w.copy(size = w.size * 1.15f, opacity = 0.9f)
                ControlKind.JOYSTICK -> w.copy(size = 0.20f)
                else -> w
            }
        },
    )

    fun leftHanded(): ControlLayout {
        val base = defaultLayout("Left-handed")
        return base.copy(
            id = "preset-left",
            preset = ControlPreset.LEFT_HANDED.id,
            widgets = base.widgets.map { w ->
                w.copy(x = 1f - w.x)
            },
        )
    }

    fun all(): List<ControlLayout> = listOf(
        defaultLayout(),
        compact(),
        pvp(),
        leftHanded(),
        defaultLayout("Survival").copy(id = "preset-survival", preset = ControlPreset.SURVIVAL.id),
        defaultLayout("Creative").copy(id = "preset-creative", preset = ControlPreset.CREATIVE.id),
        defaultLayout("Right-handed").copy(id = "preset-right", preset = ControlPreset.RIGHT_HANDED.id),
    )
}
