package com.flintlauncher.android.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stores opaque account secrets encrypted with an AES-256 key held by Android Keystore.
 *
 * Only opaque references belong in AccountRepository. Never log values passed to this
 * class. A Keystore/decryption failure is surfaced to the caller; encrypted data is
 * deliberately not discarded or replaced automatically.
 */
interface TokenVault {
    fun put(reference: String, secret: String)
    fun get(reference: String): String?
    fun remove(reference: String)
}

class AndroidKeystoreTokenVault(
    private val file: File,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val json: Json = Json,
) : TokenVault {
    private val lock = Any()
    private val atomicFile = AtomicFile(file)

    override fun put(reference: String, secret: String) = synchronized(lock) {
        require(reference.isNotBlank()) { "Token reference must not be blank" }
        require(secret.isNotEmpty()) { "Secret must not be empty" }
        val values = readAll()
        values[reference] = secret
        writeAll(values)
    }

    override fun get(reference: String): String? = synchronized(lock) {
        require(reference.isNotBlank()) { "Token reference must not be blank" }
        readAll()[reference]
    }

    override fun remove(reference: String) = synchronized(lock) {
        require(reference.isNotBlank()) { "Token reference must not be blank" }
        val values = readAll()
        if (values.remove(reference) != null) writeAll(values)
    }

    private fun readAll(): MutableMap<String, String> {
        if (!atomicFile.baseFile.exists()) return mutableMapOf()
        val encrypted = atomicFile.openRead().use { it.readBytes() }
        require(encrypted.size > NONCE_BYTES) { "Encrypted token store is truncated" }
        val nonce = encrypted.copyOfRange(0, NONCE_BYTES)
        val ciphertext = encrypted.copyOfRange(NONCE_BYTES, encrypted.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, nonce))
        val plaintext = cipher.doFinal(ciphertext)
        return json.decodeFromString<Map<String, String>>(plaintext.toString(Charsets.UTF_8)).toMutableMap()
    }

    private fun writeAll(values: Map<String, String>) {
        val plaintext = json.encodeToString(values).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val nonce = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        val output = atomicFile.startWrite()
        try {
            output.write(nonce)
            output.write(ciphertext)
            atomicFile.finishWrite(output)
        } catch (failure: Throwable) {
            atomicFile.failWrite(output)
            throw failure
        } finally {
            plaintext.fill(0)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(KEY_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "flint.account.tokens.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
    }
}
