package com.flintlauncher.android.mods

/**
 * One mod slot in a profile, with enough metadata to detect conflicts without parsing the
 * JAR's fabric.mod.json / mods.toml (which would require loading the jar). Modrinth
 * provides the project/version ids and the dependency list at install time; we record
 * what we need here.
 */
data class ModSlot(
    val projectId: String,
    val versionId: String,
    val fileName: String,
    val fileHash: String? = null,
    val incompatibleProjectIds: List<String> = emptyList(),
    val requiredProjectIds: List<String> = emptyList(),
)

data class ModConflict(
    val kind: Kind,
    val projects: List<String>,
    val message: String,
) {
    enum class Kind {
        /** Same project installed twice under different files. */
        DUPLICATE_PROJECT,
        /** Same file content (hash) installed under two names. */
        DUPLICATE_FILE,
        /** Mod A declares mod B incompatible, and B is present. */
        INCOMPATIBLE_DEPENDENCY,
        /** Mod A requires project B, but B is not installed. */
        MISSING_REQUIRED_DEPENDENCY,
    }
}

/**
 * Pure conflict detection over a profile's mod slots. Run after every install/uninstall
 * and before launch; the UI surfaces [ModConflict]s so the user can disable one side.
 * Order of slots does not matter — every conflict is reported once.
 */
object ModConflictDetector {
    fun detect(slots: List<ModSlot>): List<ModConflict> {
        val conflicts = mutableListOf<ModConflict>()

        // Duplicate project id
        slots.groupBy { it.projectId }.filterValues { it.size > 1 }.forEach { (project, group) ->
            conflicts += ModConflict(
                ModConflict.Kind.DUPLICATE_PROJECT,
                listOf(project),
                "Project $project is installed ${group.size} times (${group.joinToString { it.fileName }})",
            )
        }

        // Duplicate file hash
        slots.filter { it.fileHash != null }.groupBy { it.fileHash }.filterValues { it.size > 1 }
            .forEach { (hash, group) ->
                conflicts += ModConflict(
                    ModConflict.Kind.DUPLICATE_FILE,
                    group.map { it.projectId },
                    "Identical file (hash $hash) installed as ${group.joinToString { it.fileName }}",
                )
            }

        val installedProjects = slots.map { it.projectId }.toSet()

        // Incompatible + missing required
        for (slot in slots) {
            for (blocked in slot.incompatibleProjectIds) {
                if (blocked in installedProjects && blocked != slot.projectId) {
                    conflicts += ModConflict(
                        ModConflict.Kind.INCOMPATIBLE_DEPENDENCY,
                        listOf(slot.projectId, blocked),
                        "${slot.projectId} declares $blocked incompatible, but both are installed",
                    )
                }
            }
            for (needed in slot.requiredProjectIds) {
                if (needed !in installedProjects) {
                    conflicts += ModConflict(
                        ModConflict.Kind.MISSING_REQUIRED_DEPENDENCY,
                        listOf(slot.projectId, needed),
                        "${slot.projectId} requires $needed, which is not installed",
                    )
                }
            }
        }

        return conflicts.distinctBy { it.kind to it.projects.sorted() }
    }
}
