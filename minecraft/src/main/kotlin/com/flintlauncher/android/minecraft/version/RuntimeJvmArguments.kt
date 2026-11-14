package com.flintlauncher.android.minecraft.version

/**
 * Converts the launcher's persisted Java settings into argv tokens.
 *
 * This is deliberately not a shell parser: no command substitution, expansion,
 * pipes, redirects, or environment interpolation are performed.
 */
object RuntimeJvmArguments {
    private const val MAX_INPUT_LENGTH = 2_048
    private const val MAX_ARGUMENTS = 64
    private const val MAX_ARGUMENT_LENGTH = 512

    fun build(heapMb: Int, customArguments: String): List<String> {
        if (heapMb !in 256..16_384) {
            throw LaunchConfigurationException("Java heap must be between 256 MB and 16384 MB")
        }
        if (customArguments.length > MAX_INPUT_LENGTH) {
            throw LaunchConfigurationException("Custom JVM arguments exceed 2048 characters")
        }

        val parsed = tokenize(customArguments)
        if (parsed.size > MAX_ARGUMENTS) {
            throw LaunchConfigurationException("Custom JVM arguments contain more than 64 entries")
        }
        parsed.forEach { argument ->
            if (argument.length > MAX_ARGUMENT_LENGTH) {
                throw LaunchConfigurationException("A custom JVM argument exceeds 512 characters")
            }
            val normalized = argument.lowercase()
            if (
                normalized.startsWith("-xmx") ||
                normalized.startsWith("-xms") ||
                normalized == "-cp" ||
                normalized == "-classpath" ||
                normalized == "--class-path" ||
                normalized == "-jar" ||
                argument.startsWith("@")
            ) {
                throw LaunchConfigurationException(
                    "Custom JVM argument '$argument' conflicts with Flint's managed heap/classpath or argument-file handling",
                )
            }
        }
        return listOf("-Xmx${heapMb}M") + parsed
    }

    private fun tokenize(input: String): List<String> {
        val result = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var escaping = false
        var tokenStarted = false

        fun finishToken() {
            if (tokenStarted) {
                if (token.isEmpty()) {
                    throw LaunchConfigurationException("Empty custom JVM arguments are not allowed")
                }
                result += token.toString()
                token.setLength(0)
                tokenStarted = false
            }
        }

        input.forEach { char ->
            if (char == '\u0000' || char == '\n' || char == '\r') {
                throw LaunchConfigurationException("Custom JVM arguments cannot contain NUL or newline characters")
            }
            when {
                escaping -> {
                    token.append(char)
                    escaping = false
                    tokenStarted = true
                }
                char == '\\\\' && quote != '\'' -> {
                    escaping = true
                    tokenStarted = true
                }
                quote != null && char == quote -> {
                    quote = null
                    tokenStarted = true
                }
                quote == null && (char == '"' || char == '\'') -> {
                    quote = char
                    tokenStarted = true
                }
                quote == null && char.isWhitespace() -> finishToken()
                else -> {
                    token.append(char)
                    tokenStarted = true
                }
            }
        }
        if (escaping) {
            throw LaunchConfigurationException("Custom JVM arguments end with an incomplete escape")
        }
        if (quote != null) {
            throw LaunchConfigurationException("Custom JVM arguments contain an unterminated quote")
        }
        finishToken()
        return result
    }
}
