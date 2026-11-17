package com.flintlauncher.android.minecraft.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyArgumentTokenizerTest {
    @Test
    fun preservesQuotedArgumentsAsSingleTokens() {
        assertEquals(
            listOf("--username", "Player One", "--gameDir", "/data/user files/game"),
            LegacyArgumentTokenizer.tokenize("""--username "Player One" --gameDir '/data/user files/game'"""),
        )
    }

    @Test
    fun escapedWhitespaceAndQuotesArePreservedWithoutShellExpansion() {
        assertEquals(
            listOf("-Dpath=/data/user files/game", "-Dvalue=\\\$HOME", "--label=two words"),
            LegacyArgumentTokenizer.tokenize("-Dpath=/data/user\\ files/game -Dvalue=\\\$HOME --label=two\\ words"),
        )
        assertEquals(
            listOf("--text=quoted value"),
            LegacyArgumentTokenizer.tokenize("""--text="quoted value""""),
        )
    }

    @Test
    fun preservesBackslashesThatDoNotEscapeSupportedCharacters() {
        assertEquals(
            listOf("--path=C:\\Users\\Player\\.minecraft"),
            LegacyArgumentTokenizer.tokenize("--path=C:\\Users\\Player\\.minecraft"),
        )
    }

    @Test
    fun rejectsUnterminatedQuotes() {
        val error = runCatching { LegacyArgumentTokenizer.tokenize("""--name "unfinished""") }.exceptionOrNull()
        assertTrue(error is LaunchConfigurationException)
    }

    @Test
    fun emptyInputAndRepeatedWhitespaceProduceNoEmptyTokens() {
        assertEquals(emptyList<String>(), LegacyArgumentTokenizer.tokenize("  \t  "))
        assertEquals(listOf("--one", "--two"), LegacyArgumentTokenizer.tokenize("--one   --two"))
    }
}
