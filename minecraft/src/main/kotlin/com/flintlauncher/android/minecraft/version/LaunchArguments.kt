package com.flintlauncher.android.minecraft.version

import com.flintlauncher.android.minecraft.manifest.MojangJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class LaunchConfigurationException(message: String) : IllegalStateException(message)

data class LaunchContext(
    val playerName: String,
    val uuid: String,
    val accessToken: String,
    /** `msa` for Microsoft accounts, `legacy` for offline profiles. */
    val userType: String,
    val versionName: String,
    val versionType: String,
    val gameDirectory: String,
    val assetsRoot: String,
    val assetsIndexName: String,
    val nativesDirectory: String,
    val libraryDirectory: String,
    val classpath: String,
    val launcherVersion: String,
    val launcherName: String = "Flint",
    val clientId: String = "",
    val xuid: String = "",
    val resolutionWidth: Int? = null,
    val resolutionHeight: Int? = null,
)

data class LaunchCommand(
    val jvmArguments: List<String>,
    val mainClass: String,
    val gameArguments: List<String>,
) {
    /** Everything after the java executable. */
    fun toArgumentList(): List<String> = jvmArguments + mainClass + gameArguments
}

object LaunchCommandBuilder {
    private val placeholder = Regex("\\$\\{([A-Za-z0-9_]+)}")

    private val legacyJvm = listOf(
        "-Djava.library.path=\${natives_directory}",
        "-cp",
        "\${classpath}",
    )

    /**
     * Builds the JVM arguments, main class and game arguments from a (merged) version JSON.
     * Throws [LaunchConfigurationException] with a readable message instead of producing a
     * command that would fail later: missing main class or unresolved placeholders.
     */
    fun build(
        version: VersionJson,
        context: LaunchContext,
        environment: Environment = Environment(),
        extraJvmArguments: List<String> = emptyList(),
        extraGameArguments: List<String> = emptyList(),
        runtimeHeapMb: Int? = null,
        customJvmArguments: String = "",
    ): LaunchCommand {
        val mainClass = version.mainClass
            ?: throw LaunchConfigurationException("Version ${version.id} has no main class")

        val env = environment.copy(
            features = environment.features + mapOf(
                "has_custom_resolution" to (context.resolutionWidth != null && context.resolutionHeight != null),
            ),
        )
        val values = placeholders(context)

        val rawJvm = version.arguments?.jvm?.takeIf { it.isNotEmpty() }?.let { resolve(it, env) } ?: legacyJvm
        val rawGame = version.arguments?.game?.takeIf { it.isNotEmpty() }?.let { resolve(it, env) }
            ?: version.minecraftArguments?.split(' ')?.filter { it.isNotEmpty() }
            ?: throw LaunchConfigurationException("Version ${version.id} has no game arguments")

        val runtimeSettingsArguments = if (runtimeHeapMb != null) {
            RuntimeJvmArguments.build(runtimeHeapMb, customJvmArguments)
        } else {
            RuntimeJvmArguments.parseCustomArguments(customJvmArguments)
        }
        val jvm = extraJvmArguments + rawJvm.map { substitute(it, values) } + runtimeSettingsArguments
        val game = rawGame.map { substitute(it, values) } + extraGameArguments

        val unresolved = (jvm + game).flatMap { placeholder.findAll(it).map { m -> m.groupValues[1] }.toList() }.distinct()
        if (unresolved.isNotEmpty()) {
            throw LaunchConfigurationException("Unresolved launch placeholders: ${unresolved.joinToString()}")
        }
        return LaunchCommand(jvm, mainClass, game)
    }

    internal fun placeholders(c: LaunchContext): Map<String, String> = buildMap {
        put("auth_player_name", c.playerName)
        put("auth_uuid", c.uuid)
        put("auth_access_token", c.accessToken)
        put("auth_session", "token:${c.accessToken}:${c.uuid}")
        put("user_type", c.userType)
        put("user_properties", "{}")
        put("clientid", c.clientId)
        put("auth_xuid", c.xuid)
        put("version_name", c.versionName)
        put("version_type", c.versionType)
        put("game_directory", c.gameDirectory)
        put("assets_root", c.assetsRoot)
        put("game_assets", c.assetsRoot)
        put("assets_index_name", c.assetsIndexName)
        put("natives_directory", c.nativesDirectory)
        put("library_directory", c.libraryDirectory)
        put("classpath", c.classpath)
        put("classpath_separator", ":")
        put("launcher_name", c.launcherName)
        put("launcher_version", c.launcherVersion)
        c.resolutionWidth?.let { put("resolution_width", it.toString()) }
        c.resolutionHeight?.let { put("resolution_height", it.toString()) }
    }

    internal fun substitute(text: String, values: Map<String, String>): String =
        placeholder.replace(text) { match -> values[match.groupValues[1]] ?: match.value }

    private fun resolve(entries: List<JsonElement>, env: Environment): List<String> =
        entries.flatMap { element ->
            when (element) {
                is JsonPrimitive -> listOf(element.content)
                is JsonObject -> {
                    val rules = element["rules"]?.let {
                        MojangJson.decodeFromJsonElement(ListSerializer(Rule.serializer()), it)
                    }
                    if (!RuleEvaluator.allows(rules, env)) {
                        emptyList()
                    } else {
                        when (val value = element["value"]) {
                            is JsonPrimitive -> listOf(value.content)
                            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content }
                            else -> emptyList()
                        }
                    }
                }
                else -> emptyList()
            }
        }
}
