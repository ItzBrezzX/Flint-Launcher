package com.flintlauncher.android

import com.flintlauncher.android.ui.controls.ControlLayout
import com.flintlauncher.android.ui.controls.ControlPresets
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ControlLayoutRepository(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): ControlLayout {
        if (!file.isFile) return ControlPresets.defaultLayout()
        return runCatching {
            json.decodeFromString<ControlLayout>(file.readText()).also(::validate)
        }.getOrElse { ControlPresets.defaultLayout() }
    }

    fun save(layout: ControlLayout) {
        validate(layout)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(json.encodeToString(layout))
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun validate(layout: ControlLayout) {
        require(layout.widgets.isNotEmpty()) { "Control layout must contain at least one widget" }
        require(layout.widgets.size <= 64) { "Control layout contains too many widgets" }
        require(layout.widgets.map { it.id }.distinct().size == layout.widgets.size) { "Control widget IDs must be unique" }
        layout.widgets.forEach { widget ->
            require(widget.id.isNotBlank() && widget.id.length <= 64) { "Invalid control widget ID" }
            require(widget.x.isFinite() && widget.x in 0f..1f) { "Invalid widget X coordinate" }
            require(widget.y.isFinite() && widget.y in 0f..1f) { "Invalid widget Y coordinate" }
            require(widget.size.isFinite() && widget.size in 0.04f..0.5f) { "Invalid widget size" }
            require(widget.opacity.isFinite() && widget.opacity in 0f..1f) { "Invalid widget opacity" }
        }
    }
}
