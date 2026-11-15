package com.flintlauncher.android.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyGraphResolverTest {
    private data class Node(
        override val nodeId: String,
        override val projectId: String,
        override val required: List<DepRef> = emptyList(),
        override val incompatible: List<DepRef> = emptyList(),
    ) : DependencyNode

    private fun graph(nodes: Map<String, Node>) = { ref: DepRef ->
        ref.versionId?.let { v -> nodes.values.firstOrNull { it.nodeId == v } }
            ?: ref.projectId?.let { p -> nodes[p] }
    }

    @Test
    fun resolvesLinearChainInDependencyOrder() {
        val c = Node("v-c", "C")
        val b = Node("v-b", "B", required = listOf(DepRef(projectId = "C")))
        val a = Node("v-a", "A", required = listOf(DepRef(projectId = "B")))
        val nodes = mapOf("A" to a, "B" to b, "C" to c)
        val result = DependencyGraphResolver.resolve(a, graph(nodes))
        assertTrue(result is ResolutionResult.Success)
        assertEquals(listOf("v-c", "v-b", "v-a"), (result as ResolutionResult.Success).installOrder)
    }

    @Test
    fun sharedDependencyIsInstalledOnce() {
        val shared = Node("v-shared", "Shared")
        val left = Node("v-left", "Left", required = listOf(DepRef(projectId = "Shared")))
        val right = Node("v-right", "Right", required = listOf(DepRef(projectId = "Shared")))
        val root = Node("v-root", "Root", required = listOf(DepRef(projectId = "Left"), DepRef(projectId = "Right")))
        val nodes = mapOf("Root" to root, "Left" to left, "Right" to right, "Shared" to shared)
        val result = DependencyGraphResolver.resolve(root, graph(nodes)) as ResolutionResult.Success
        assertEquals(4, result.installOrder.size)
        assertEquals(1, result.installOrder.count { it == "v-shared" })
    }

    @Test
    fun cycleIsDetected() {
        val a = Node("v-a", "A", required = listOf(DepRef(projectId = "B")))
        val b = Node("v-b", "B", required = listOf(DepRef(projectId = "A")))
        val result = DependencyGraphResolver.resolve(a, graph(mapOf("A" to a, "B" to b)))
        assertTrue(result is ResolutionResult.Cycle)
        assertTrue((result as ResolutionResult.Cycle).path.containsAll(listOf("A", "B")))
    }

    @Test
    fun missingRequiredDependencyIsReported() {
        val a = Node("v-a", "A", required = listOf(DepRef(projectId = "Ghost")))
        val result = DependencyGraphResolver.resolve(a, graph(mapOf("A" to a)))
        assertTrue(result is ResolutionResult.Missing)
        assertEquals("Ghost", (result as ResolutionResult.Missing).ref.projectId)
    }

    @Test
    fun incompatibleAlreadyInstalledBlocksResolution() {
        val lib = Node("v-lib", "OldLib")
        val mod = Node("v-mod", "Modern", incompatible = listOf(DepRef(projectId = "OldLib")))
        // Simulate OldLib already installed by making the root depend on it first
        val root = Node("v-root", "Root", required = listOf(DepRef(projectId = "OldLib"), DepRef(projectId = "Modern")))
        val result = DependencyGraphResolver.resolve(root, graph(mapOf("Root" to root, "OldLib" to lib, "Modern" to mod)))
        assertTrue(result is ResolutionResult.Incompatible)
        assertEquals("Modern", (result as ResolutionResult.Incompatible).nodeProject)
        assertEquals("OldLib", result.blockedProject)
    }

    @Test
    fun tooDeepIsReported() {
        var current = Node("v-0", "N0")
        val nodes = mutableMapOf("N0" to current)
        for (i in 1..20) {
            val next = Node("v-$i", "N$i", required = listOf(DepRef(projectId = "N${i - 1}")))
            nodes["N$i"] = next
            current = next
        }
        val result = DependencyGraphResolver.resolve(current, graph(nodes), maxDepth = 5)
        assertTrue(result is ResolutionResult.TooDeep)
    }
}

class ModConflictDetectorTest {
    @Test
    fun duplicateProjectIsACConflict() {
        val slots = listOf(
            ModSlot("p1", "v1", "a.jar"),
            ModSlot("p1", "v2", "b.jar"),
        )
        val conflicts = ModConflictDetector.detect(slots)
        assertEquals(1, conflicts.size)
        assertEquals(ModConflict.Kind.DUPLICATE_PROJECT, conflicts.single().kind)
    }

    @Test
    fun duplicateFileHashIsDetected() {
        val slots = listOf(
            ModSlot("p1", "v1", "a.jar", fileHash = "abc"),
            ModSlot("p2", "v2", "b.jar", fileHash = "abc"),
        )
        assertEquals(ModConflict.Kind.DUPLICATE_FILE, ModConflictDetector.detect(slots).single().kind)
    }

    @Test
    fun incompatiblePairIsDetected() {
        val slots = listOf(
            ModSlot("sodium", "v1", "sodium.jar", incompatibleProjectIds = listOf("optifine")),
            ModSlot("optifine", "v2", "optifine.jar"),
        )
        val c = ModConflictDetector.detect(slots)
        assertEquals(1, c.size)
        assertEquals(ModConflict.Kind.INCOMPATIBLE_DEPENDENCY, c.single().kind)
        assertTrue(c.single().projects.containsAll(listOf("sodium", "optifine")))
    }

    @Test
    fun missingRequiredDependencyIsDetected() {
        val slots = listOf(
            ModSlot("modmenu", "v1", "mm.jar", requiredProjectIds = listOf("fabric-api")),
        )
        val c = ModConflictDetector.detect(slots)
        assertEquals(ModConflict.Kind.MISSING_REQUIRED_DEPENDENCY, c.single().kind)
        assertTrue(c.single().message.contains("fabric-api"))
    }

    @Test
    fun cleanProfileHasNoConflicts() {
        val slots = listOf(
            ModSlot("fabric-api", "v1", "fapi.jar"),
            ModSlot("sodium", "v2", "sodium.jar", requiredProjectIds = listOf("fabric-api")),
        )
        assertTrue(ModConflictDetector.detect(slots).isEmpty())
    }
}

class ModCompatibilityCheckerTest {
    private fun version(gameVersions: List<String>, loaders: List<String>, hasJar: Boolean = true) = ModrinthVersion(
        id = "v", projectId = "p", versionNumber = "1.0",
        gameVersions = gameVersions, loaders = loaders,
        files = if (hasJar) listOf(ModrinthFile(url = "https://x/y.jar", filename = "y.jar")) else emptyList(),
    )

    @Test
    fun matchingVersionAndLoaderIsCompatible() {
        val report = ModCompatibilityChecker.check(
            version(listOf("1.20.4", "1.21"), listOf("fabric", "quilt")),
            "1.20.4", "fabric",
        )
        assertTrue(report.compatible)
        assertTrue(report.reasons.isEmpty())
    }

    @Test
    fun mismatchedMinecraftVersionIsRejectedWithReason() {
        val report = ModCompatibilityChecker.check(version(listOf("1.21"), listOf("fabric")), "1.20.4", "fabric")
        assertTrue(!report.compatible)
        assertTrue(report.reasons.single().contains("Minecraft 1.20.4"))
    }

    @Test
    fun mismatchedLoaderIsRejected() {
        val report = ModCompatibilityChecker.check(version(listOf("1.21"), listOf("forge")), "1.21", "fabric")
        assertTrue(!report.compatible)
        assertTrue(report.reasons.single().contains("loader"))
    }

    @Test
    fun versionWithoutJarIsRejected() {
        val report = ModCompatibilityChecker.check(version(listOf("1.21"), listOf("fabric"), hasJar = false), "1.21", "fabric")
        assertTrue(!report.compatible)
        assertTrue(report.reasons.single().contains("JAR"))
    }

    @Test
    fun pickCompatibleReturnsFirstMatching() {
        val versions = listOf(
            version(listOf("1.21"), listOf("forge")).copy(id = "forge-only"),
            version(listOf("1.20.4"), listOf("fabric")).copy(id = "first-fabric"),
            version(listOf("1.20.4"), listOf("fabric", "quilt")).copy(id = "second-fabric"),
        )
        val picked = ModCompatibilityChecker.pickCompatible(versions, "1.20.4", "fabric")
        assertEquals("first-fabric", picked?.id)
    }
}

class LoaderInstallerContractTest {
    @Test
    fun forgeUrlAndVersionIdFollowMavenConvention() {
        val url = ForgeInstallerContract.installerUrl("1.20.1", "47.1.0")
        assertTrue(url.contains("maven.minecraftforge.net"))
        assertTrue(url.endsWith("forge-1.20.1-47.1.0-installer.jar"))
        assertEquals("1.20.1-forge-47.1.0", ForgeInstallerContract.installedVersionId("1.20.1", "47.1.0"))
        assertTrue(ForgeInstallerContract.spec(java.io.File("i.jar"), java.io.File("/g"), "1.20.1", "47.1.0")
            .args.contains("--installClient"))
    }

    @Test
    fun neoforgeUrlAndVersionIdFollowMavenConvention() {
        val url = NeoForgeInstallerContract.installerUrl("21.0.0")
        assertTrue(url.contains("maven.neoforged.net"))
        assertTrue(url.endsWith("neoforge-21.0.0-installer.jar"))
        assertEquals("neoforge-21.0.0", NeoForgeInstallerContract.installedVersionId("21.0.0"))
        assertTrue(NeoForgeInstallerContract.spec(java.io.File("i.jar"), java.io.File("/g"), "21.0.0")
            .args.contains("--install-client"))
    }
}
