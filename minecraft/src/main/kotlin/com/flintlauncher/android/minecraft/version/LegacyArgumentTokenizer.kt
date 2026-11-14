package com.flintlauncher.android.minecraft.version

/**
 * Tokenizes the pre-1.13 `minecraftArguments` string into JVM argv entries.
 *
 * This is intentionally not a shell: it performs no expansion, command
 * substitution, globbing, or redirection. Quotes group whitespace into one
 * argument and are removed. Backslashes only escape quotes, backslashes,
 * or whitespace; other backslashes are preserved.
 */
internal object LegacyArgumentTokenizer {
    fun tokenize(input: String): List<String> {
        val result = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var tokenStarted = false
        var index = 0

        fun finishToken() {
            if (tokenStarted) {
                result += token.toString()
                token.setLength(0)
                tokenStarted = false
            }
        }

        while (index < input.length) {
            val char = input[index]
            when {
                quote != null && char == quote -> {
                    quote = null
                    tokenStarted = true
                }
                quote != null && char == '\\' &&
                    index + 1 < input.length &&
                    (input[index + 1] == quote || input[index + 1] == '\\') -> {
                    token.append(input[index + 1])
                    tokenStarted = true
                    index++
                }
                quote == null && (char == '"' || char == '\'') -> {
                    quote = char
                    tokenStarted = true
                }
                quote == null && char.isWhitespace() -> finishToken()
                quote == null && char == '\\' &&
                    index + 1 < input.length &&
                    (input[index + 1].isWhitespace() || input[index + 1] == '"' ||
                        input[index + 1] == '\'' || input[index + 1] == '\\') -> {
                    token.append(input[index + 1])
                    tokenStarted = true
                    index++
                }
                else -> {
                    token.append(char)
                    tokenStarted = true
                }
            }
            index++
        }

        if (quote != null) {
            throw LaunchConfigurationException("Legacy Minecraft arguments contain an unterminated quote")
        }
        finishToken()
        return result
    }
}
