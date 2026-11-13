package com.flintlauncher.android.ui.versions

import java.util.Locale

object Formatters {
    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    fun bytes(value: Long): String = when {
        value < 1024 -> "$value B"
        value < MB -> String.format(Locale.US, "%.1f KB", value / KB)
        value < GB -> String.format(Locale.US, "%.1f MB", value / MB)
        else -> String.format(Locale.US, "%.2f GB", value / GB)
    }

    fun speed(bytesPerSecond: Long): String = bytes(bytesPerSecond) + "/s"

    /** `mm:ss`, or `h:mm:ss` from one hour up. Null (unknown) shows as `--:--`. */
    fun duration(seconds: Long?): String {
        if (seconds == null || seconds < 0) return "--:--"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun percent(value: Double?): String =
        if (value == null) "--" else String.format(Locale.US, "%.1f%%", value.coerceIn(0.0, 100.0))
}
