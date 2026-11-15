package com.flintlauncher.android.mods

/**
 * Pure, network-free dependency-graph resolver. Mod loaders (Fabric/Quilt/Forge/NeoForge)
 * all express the same shape: a node declares required, optional and incompatible
 * dependencies by project (and optionally pinned version). This resolver walks required
 * dependencies in depth-first order, detects cycles and missing nodes, and returns the
 * install order (dependencies before dependents).
 *
 * It does not fetch anything — [lookup] is supplied by the caller (Modrinth API, a local
 * cache, or a test fixture). That keeps the logic unit-testable without a mock server.
 */
interface DependencyNode {
    /** Stable per-version id (Modrinth version id, or a synthetic id for local mods). */
    val nodeId: String
    val projectId: String
    val required: List<DepRef>
    val incompatible: List<DepRef>
}

data class DepRef(val projectId: String? = null, val versionId: String? = null) {
    init { require(projectId != null || versionId != null) { "DepRef needs a project or version id" } }
}

sealed interface ResolutionResult {
    /** Nodes in install order: dependencies before the root. */
    data class Success(val installOrder: List<String>, val optionalSkipped: Int = 0) : ResolutionResult
    data class Cycle(val path: List<String>) : ResolutionResult
    data class Missing(val ref: DepRef) : ResolutionResult
    data class Incompatible(val nodeProject: String, val blockedProject: String) : ResolutionResult
    data class TooDeep(val atNode: String) : ResolutionResult
}

object DependencyGraphResolver {
    const val DEFAULT_MAX_DEPTH = 16

    fun <T : DependencyNode> resolve(
        root: T,
        lookup: (DepRef) -> T?,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
    ): ResolutionResult {
        val order = mutableListOf<String>()
        val installed = mutableSetOf<String>()
        val visiting = mutableListOf<String>()

        fun walk(node: T, depth: Int): ResolutionResult? {
            if (depth > maxDepth) return ResolutionResult.TooDeep(node.nodeId)
            if (node.projectId in installed) return null
            if (node.projectId in visiting) {
                return ResolutionResult.Cycle(visiting.dropWhile { it != node.projectId } + node.projectId)
            }
            visiting += node.projectId
            try {
                for (block in node.incompatible) {
                    val blockedProject = block.projectId
                    if (blockedProject != null && blockedProject in installed) {
                        return ResolutionResult.Incompatible(node.projectId, blockedProject)
                    }
                }
                for (dep in node.required) {
                    val resolved = lookup(dep)
                        ?: return ResolutionResult.Missing(dep)
                    val err = walk(resolved, depth + 1)
                    if (err != null) return err
                }
            } finally {
                visiting.removeAt(visiting.lastIndex)
            }
            installed += node.projectId
            order += node.nodeId
            return null
        }

        return walk(root, 0) ?: ResolutionResult.Success(order)
    }
}
