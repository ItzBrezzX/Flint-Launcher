package com.flintlauncher.android.runtime

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Stages the APK's Android/Bionic LWJGL and renderer libraries in the per-game
 * native directory used by both java.library.path and org.lwjgl.librarypath.
 *
 * Linux/GLIBC .so files from Mojang's natives-linux jars are deliberately not
 * accepted here. Every staged file is checked for ELF class and e_machine.
 */
object AndroidLwjglNativesInstaller {
    private data class ElfAbi(val elfClass: Int, val machine: Int)

    private val requiredLibraries = listOf(
        "liblwjgl.so",
        "liblwjgl_opengl.so",
        "libglfw.so",
        "libflint_game_glfw.so",
        "libGL.so",
    )

    private val abiSpecs = mapOf(
        "arm64" to ElfAbi(elfClass = 2, machine = 183), // ELF64, EM_AARCH64
        "arm64-v8a" to ElfAbi(elfClass = 2, machine = 183),
        "aarch64" to ElfAbi(elfClass = 2, machine = 183),
        "arm" to ElfAbi(elfClass = 1, machine = 40), // ELF32, EM_ARM
        "armeabi-v7a" to ElfAbi(elfClass = 1, machine = 40),
        "x86" to ElfAbi(elfClass = 1, machine = 3), // ELF32, EM_386
        "i386" to ElfAbi(elfClass = 1, machine = 3),
        "x86_64" to ElfAbi(elfClass = 2, machine = 62), // ELF64, EM_X86_64
        "amd64" to ElfAbi(elfClass = 2, machine = 62),
    )

    /**
     * Copies the five runtime-critical libraries from the APK's nativeLibraryDir.
     * Throws with a specific reason when an ABI artifact is missing or mismatched.
     */
    fun install(
        sourceLibraryDir: File,
        targetDirectory: File,
        architecture: String,
    ): List<File> {
        val expectedAbi = abiSpecs[architecture.trim().lowercase()]
            ?: throw IllegalArgumentException("Unsupported Android native ABI: $architecture")
        require(sourceLibraryDir.isDirectory) {
            "APK native library directory does not exist: ${sourceLibraryDir.absolutePath}"
        }
        if (!targetDirectory.isDirectory && !targetDirectory.mkdirs()) {
            throw IllegalStateException("Could not create Minecraft native directory: ${targetDirectory.absolutePath}")
        }

        val staged = ArrayList<File>(requiredLibraries.size)
        for (libraryName in requiredLibraries) {
            val source = File(sourceLibraryDir, libraryName)
            check(source.isFile && source.length() > 0L) {
                "Required Android native library is missing: ${source.absolutePath}"
            }
            val sourceAbi = readElfAbi(source)
            check(sourceAbi == expectedAbi) {
                "Wrong ABI for $libraryName: expected $expectedAbi, found $sourceAbi"
            }

            val destination = File(targetDirectory, libraryName)
            val temporary = File(targetDirectory, ".$libraryName.flint-tmp")
            try {
                FileInputStream(source).use { input ->
                    FileOutputStream(temporary).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                val copiedAbi = readElfAbi(temporary)
                check(copiedAbi == expectedAbi) {
                    "Copied Android library $libraryName failed ABI validation: $copiedAbi"
                }
                try {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } finally {
                if (temporary.exists()) temporary.delete()
            }
            check(destination.isFile && destination.length() > 0L) {
                "Failed to stage Android native library: ${destination.absolutePath}"
            }
            staged += destination
        }
        return staged
    }

    private fun readElfAbi(file: File): ElfAbi {
        val header = ByteArray(20)
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read < 0) {
                    throw IllegalStateException("Truncated ELF header: ${file.absolutePath}")
                }
                offset += read
            }
        }

        check(
            header[0] == 0x7f.toByte() &&
                header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() &&
                header[3] == 'F'.code.toByte(),
        ) { "Not an ELF shared library: ${file.absolutePath}" }

        val elfClass = header[4].toInt() and 0xff
        val dataEncoding = header[5].toInt() and 0xff
        check(dataEncoding == 1) { "Only little-endian Android ELF libraries are supported: ${file.absolutePath}" }
        val machine = ByteBuffer.wrap(header, 18, 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
            .toInt() and 0xffff
        return ElfAbi(elfClass, machine)
    }
}
