package com.flintlauncher.android.minecraft.version

/**
 * What the launcher is running on, as seen by Mojang's rules. Mojang names operating
 * systems `windows`, `osx` and `linux`. Android uses linux-style natives.
 */
data class Environment(
    val osName: String = "linux",
    val osArch: String = "arm64",
    val osVersion: String? = null,
    val features: Map<String, Boolean> = emptyMap(),
) {
    /** Value substituted for `${arch}` in native classifier names. */
    val bitness: String get() = if (osArch.contains("64")) "64" else "32"
}

object RuleEvaluator {
    /**
     * No rules means allowed. Otherwise rules apply in order, the last matching rule
     * decides, and the default is "disallowed".
     */
    fun allows(rules: List<Rule>?, env: Environment): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (matches(rule, env)) allowed = rule.action == "allow"
        }
        return allowed
    }

    private fun matches(rule: Rule, env: Environment): Boolean {
        val os = rule.os
        if (os != null) {
            if (os.name != null && !os.name.equals(env.osName, ignoreCase = true)) return false
            if (os.arch != null && !os.arch.equals(env.osArch, ignoreCase = true)) return false
            if (os.version != null) {
                val version = env.osVersion ?: return false
                if (!Regex(os.version).containsMatchIn(version)) return false
            }
        }
        val features = rule.features
        if (features != null) {
            for ((key, wanted) in features) {
                if ((env.features[key] ?: false) != wanted) return false
            }
        }
        return true
    }
}
