package com.flintlauncher.android

import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.AndroidKeystoreTokenVault
import com.flintlauncher.android.account.FileAccountRepository
import com.flintlauncher.android.account.TokenVault
import com.flintlauncher.android.auth.MinecraftProfile
import com.flintlauncher.android.auth.MinecraftSession
import com.flintlauncher.android.auth.MicrosoftTokens
import com.flintlauncher.android.auth.SkinInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MicrosoftAccountStoreTest {
    private class MemoryVault : TokenVault {
        val values = mutableMapOf<String, String>()
        override fun put(reference: String, secret: String) { values[reference] = secret }
        override fun get(reference: String): String? = values[reference]
        override fun remove(reference: String) { values.remove(reference) }
    }

    private fun fixture(): Triple<MicrosoftAccountStore, AccountManager, MemoryVault> {
        val directory = Files.createTempDirectory("flint-accounts").toFile()
        val manager = AccountManager(FileAccountRepository(File(directory, "accounts.json")))
        val vault = MemoryVault()
        return Triple(MicrosoftAccountStore(manager, vault), manager, vault)
    }

    private fun tokens(refresh: String? = "refresh-secret") =
        MicrosoftTokens("access-secret", refresh, 3600)

    private fun session() = MinecraftSession(
        accessToken = "minecraft-access-secret",
        expiresInSeconds = 86400,
        profile = MinecraftProfile(
            id = "069a79f444e94726a5befca90e38aaf5",
            name = "Notch",
            skins = listOf(SkinInfo("https://textures.minecraft.net/skin", "CLASSIC", true)),
        ),
        entitlementConfirmed = true,
    )

    @Test
    fun persistsOnlyRefreshTokenAndUsesCanonicalUuid() = runBlocking {
        val (store, manager, vault) = fixture()
        val account = store.saveLogin(tokens(), session())

        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", account.uuid)
        assertEquals("Notch", account.username)
        assertEquals("refresh-secret", store.refreshToken(account.id))
        assertEquals(1, vault.values.size)
        assertFalse(vault.values.values.any { it.contains("access-secret") || it.contains("minecraft-access-secret") })
        assertEquals(account.id, manager.selected()?.id)
    }

    @Test
    fun rotatedRefreshTokenReplacesTheStoredSecret() = runBlocking {
        val (store, _, vault) = fixture()
        val account = store.saveLogin(tokens(), session())
        store.updateRefreshToken(account.id, "rotated-refresh")

        assertEquals("rotated-refresh", store.refreshToken(account.id))
        assertEquals(1, vault.values.size)
    }

    @Test
    fun removingMicrosoftAccountAlsoRemovesItsCredential() = runBlocking {
        val (store, manager, vault) = fixture()
        val account = store.saveLogin(tokens(), session())
        store.removeAccount(account.id)

        assertNull(manager.selected())
        assertNull(manager.list().firstOrNull { it.id == account.id })
        assertFalse(vault.values.containsKey(account.tokenRef))
    }

    @Test
    fun refusesToPersistLoginWithoutRefreshToken() = runBlocking {
        val (store, manager, vault) = fixture()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.saveLogin(tokens(refresh = null), session()) }
        }
        assertEquals(0, manager.list().size)
        assertEquals(0, vault.values.size)
    }

    @Test
    fun rejectsMalformedMinecraftProfileUuid() = runBlocking {
        val (store, manager, vault) = fixture()
        val invalid = session().copy(profile = session().profile.copy(id = "not-a-uuid"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.saveLogin(tokens(), invalid) }
        }
        assertEquals(0, manager.list().size)
        assertEquals(0, vault.values.size)
    }
}
