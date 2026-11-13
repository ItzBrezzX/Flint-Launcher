package com.flintlauncher.android.mods

import com.flintlauncher.android.profiles.Profile
import java.io.IOException

/**
 * Modrinth-only catalog and installation workflow.
 *
 * Required dependencies are resolved recursively from Modrinth before the requested mod is
 * installed. Optional dependencies are left optional; incompatible dependencies are rejected
 * when already present in the selected profile.
 */
class ModrinthRepository(
    private val api: ModrinthApi,
    private val installer: ModrinthModInstaller,
) {
    val downloadProgress get() = installer.downloadProgress

    suspend fun search(filters: ModrinthFilters): ModrinthSearchResult = api.search(filters)

    suspend fun tags(): ModrinthTags = api.tags()

    suspend fun versions(
        profile: Profile,
        projectId: String,
        projectType: String = "mod",
    ): List<ModrinthVersion> = api.projectVersions(
        projectId,
        profile.minecraftVersion,
        profile.loader.takeIf { projectType == "mod" },
    )

    suspend fun installed(profile: Profile): List<InstalledMod> = installer.installed(profile)

    suspend fun uninstall(profile: Profile, projectId: String): Boolean =
        installer.uninstall(profile, projectId)

    suspend fun install(
        profile: Profile,
        project: ModrinthProject,
        version: ModrinthVersion,
    ): List<InstalledMod> {
        require(project.projectType == "mod") { "Only Modrinth mods can be installed" }
        require(profile.id.isNotBlank()) { "Select or create a profile before installing mods" }
        require(profile.loader != "vanilla") { "Select a Fabric, Quilt, Forge, or NeoForge profile" }

        val installedIds = installer.installed(profile).mapTo(mutableSetOf()) { it.projectId }
        val installedNow = mutableListOf<InstalledMod>()
        val visiting = mutableSetOf<String>()

        suspend fun installNode(nodeProject: ModrinthProject, nodeVersion: ModrinthVersion, depth: Int) {
            if (nodeProject.projectId in installedIds) return
            if (depth > MAX_DEPENDENCY_DEPTH) {
                throw IOException("Modrinth dependency tree is too deep")
            }
            if (!visiting.add(nodeProject.projectId)) {
                throw IOException("Circular required dependency detected for ${nodeProject.title}")
            }
            try {
                val conflicts = nodeVersion.dependencies.filter { dependency ->
                    dependency.dependencyType == "incompatible" &&
                        dependency.projectId?.let { it in installedIds || it in visiting } == true
                }
                if (conflicts.isNotEmpty()) {
                    throw IOException("Incompatible mod dependency is already installed: ${conflicts.first().projectId}")
                }

                for (dependency in nodeVersion.dependencies.filter { it.dependencyType == "required" }) {
                    val dependencyVersion = when {
                        !dependency.versionId.isNullOrBlank() -> api.version(dependency.versionId)
                        !dependency.projectId.isNullOrBlank() ->
                            api.projectVersions(
                                dependency.projectId,
                                profile.minecraftVersion,
                                profile.loader,
                            ).firstOrNull()
                                ?: throw IOException(
                                    "No compatible required dependency version for ${dependency.projectId}",
                                )
                        else -> throw IOException("Modrinth returned a required dependency without an ID")
                    }
                    val dependencyProjectId = dependency.projectId ?: dependencyVersion.projectId
                    if (dependencyProjectId !in installedIds) {
                        val details = api.project(dependencyProjectId)
                        val dependencyProject = ModrinthProject(
                            projectId = details.id,
                            projectType = details.projectType,
                            title = details.title,
                            description = details.description,
                            slug = details.slug,
                        )
                        installNode(dependencyProject, dependencyVersion, depth + 1)
                    }
                }

                val installed = installer.install(profile, nodeProject, nodeVersion)
                installedIds += nodeProject.projectId
                installedNow += installed
            } finally {
                visiting.remove(nodeProject.projectId)
            }
        }

        installNode(project, version, depth = 0)
        return installedNow
    }

    private companion object {
        const val MAX_DEPENDENCY_DEPTH = 16
    }
}
