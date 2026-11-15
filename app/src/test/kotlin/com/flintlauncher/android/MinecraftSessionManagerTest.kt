package com.flintlauncher.android

import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.account.FileAccountRepository
import com.flintlauncher.android.account.FlintAccount
import com.flintlauncher.android.account.TokenVault
import com.flintlauncher.android.auth.AuthError
import com.flintlauncher.android.auth.AuthException
import com.flintlauncher.android.auth.MicrosoftAuthOperations
import com.flintlauncher.android.auth.MicrosoftTokens
import com.flintlauncher.android.auth.MinecraftProfile
import com.flintlauncher.android.auth.MinecraftSession
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MinecraftSessionManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var accountManager: AccountManager
    private lateinit var vault: InMemoryVault
    private lateinit var accountStore: MicrosoftAccountStore

    private class InMemoryVault : TokenVault {
        val store = mutableMapOf<String, String>()
        override fun put(reference: String, secret: String) { store[reference] = secret }
        override fun get(reference: String): String? = store[reference]
        override fun remove(reference: String) { store.remove(reference) }
    }

    private class FakeAuth(
        val refreshToken: String = "new-refresh",
        val msAccessToken: String = "ms-access",
        val mcAccessToken: String = "mc-access",
        val failRefreshWith: AuthError? = null,
    ) : MicrosoftAuthOperations {
        var refreshCalls = 0
        var loginCalls = 0
        override suspend fun refresh(refreshToken: String): MicrosoftTokens {
            refreshCalls++
            failRefreshWith?.let { throw AuthException(it) }
            return MicrosoftTokens(msAccessToken, this.refreshToken, 3600)
        }
        override suspend fun loginMinecraft(microsoftAccessToken: String): MinecraftSession {
            loginCalls++
            assertEquals(msAccessToken, microsoftAccessToken)
            return MinecraftSession(
                accessToken = mcAccessToken,
                expiresInSeconds = 3600,
                profile = MinecraftProfile("00112233445566778899aabbccddeeff", "TestPlayer", emptyList()),
                entitlementConfirmed = true,
            )
        }
    }

    @Before
    fun setUp() {
        val repo = FileAccountRepository(File(tmp.root, "accounts.json"))
        accountManager = AccountManager(repo)
        vault = InMemoryVault()
        accountStore = MicrosoftAccountStore(accountManager, vault)
    }

    @Test
    fun offlineAccountResolvesLegacyToken() = runBlocking {
        val offline = accountManager.createOfflineAccount("Steve").getOrThrow()
        val manager = MinecraftSessionManager(authClient = null, accountStore = accountStore)
        val resolved = manager.resolve(offline).getOrThrow()
        assertEquals("0", resolved.accessToken)
        assertEquals("legacy", resolved.userType)
        assertEquals("Steve", resolved.username)
    }

    @Test
    fun microsoftAccountRefreshesAndCaches() = runBlocking {
        val ref = "microsoft:ref1"
        vault.put(ref, "old-refresh")
        val account = FlintAccount(
            id = UUID.randomUUID().toString(),
            type = AccountType.MICROSOFT,
            username = "TestPlayer",
            uuid = "00112233-4455-6677-8899-aabbccddeeff",
            tokenRef = ref,
        )
        runBlocking { accountManager.addMicrosoftAccount(account) }
        val fake = FakeAuth()
        val manager = MinecraftSessionManager(authClient = fake, accountStore = accountStore)

        val first = manager.resolve(account).getOrThrow()
        assertEquals("mc-access", first.accessToken)
        assertEquals("msa", first.userType)
        assertEquals(1, fake.refreshCalls)
        assertEquals(1, fake.loginCalls)
        // Rotated refresh token persisted.
        assertEquals("new-refresh", vault.store[ref])

        val second = manager.resolve(account).getOrThrow()
        assertEquals("mc-access", second.accessToken)
        // Cache hit: no second refresh/login.
        assertEquals(1, fake.refreshCalls)
        assertEquals(1, fake.loginCalls)
    }

    @Test
    fun invalidGrantSurfacesReauthRequired() = runBlocking {
        val ref = "microsoft:ref2"
        vault.put(ref, "old-refresh")
        val account = FlintAccount(
            id = UUID.randomUUID().toString(),
            type = AccountType.MICROSOFT,
            username = "TestPlayer",
            uuid = "00112233-4455-6677-8899-aabbccddeeff",
            tokenRef = ref,
        )
        runBlocking { accountManager.addMicrosoftAccount(account) }
        val fake = FakeAuth(failRefreshWith = AuthError.InvalidGrant)
        val manager = MinecraftSessionManager(authClient = fake, accountStore = accountStore)
        val result = manager.resolve(account)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SessionError.ReauthRequired)
    }

    @Test
    fun noAuthClientTreatedAsNotConfigured() = runBlocking {
        val ref = "microsoft:ref3"
        vault.put(ref, "old-refresh")
        val account = FlintAccount(
            id = UUID.randomUUID().toString(),
            type = AccountType.MICROSOFT,
            username = "TestPlayer",
            uuid = "00112233-4455-6677-8899-aabbccddeeff",
            tokenRef = ref,
        )
        runBlocking { accountManager.addMicrosoftAccount(account) }
        val manager = MinecraftSessionManager(authClient = null, accountStore = accountStore)
        val result = manager.resolve(account)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SessionError.NotConfigured)
    }
}
