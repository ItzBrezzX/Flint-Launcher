package com.flintlauncher.android

import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GameLogReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var gameDir: File
    private lateinit var logsDir: File

    @Before
    fun setUp() {
        gameDir = tmp.newFolder("game")
        logsDir = File(gameDir, "logs").apply { mkdirs() }
    }

    @Test
    fun listLogsFindsPlainAndGzipped() {
        File(logsDir, "latest.log").writeText("hello")
        gzip(File(logsDir, "2024-01-01-1.log.gz"), "old log")
        val logs = GameLogReader.listLogs(gameDir)
        assertEquals(2, logs.size)
        assertTrue(logs.any { it.name == "latest.log" && !it.compressed })
        assertTrue(logs.any { it.name.endsWith(".gz") && it.compressed })
    }

    @Test
    fun readPlainLogTail() {
        val lines = (1..10).joinToString("\n") { "line $it" }
        val file = File(logsDir, "latest.log").apply { writeText(lines) }
        val logFile = GameLogReader.listLogs(gameDir).first { it.name == "latest.log" }
        val tail = GameLogReader.tailLines(logFile, lines = 3)
        val tailLines = tail.lines()
        assertEquals(3, tailLines.size)
        assertEquals("line 8", tailLines[0])
        assertEquals("line 10", tailLines[2])
    }

    @Test
    fun readGzippedLog() {
        gzip(File(logsDir, "old.log.gz"), "gzipped content line 1\ngzipped content line 2")
        val logFile = GameLogReader.listLogs(gameDir).first()
        val content = GameLogReader.readTail(logFile)
        assertTrue(content.contains("gzipped content line 1"))
        assertTrue(content.contains("gzipped content line 2"))
    }

    @Test
    fun missingLogsDirReturnsEmpty() {
        assertTrue(GameLogReader.listLogs(tmp.newFolder("empty")).isEmpty())
    }

    private fun gzip(target: File, content: String) {
        GZIPOutputStream(FileOutputStream(target)).use { it.write(content.toByteArray()) }
    }
}
