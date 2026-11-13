package com.flintlauncher.android.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking

class OfflineAccountTest {

    @Test
    fun offlineUuidIsDeterministicAndMatchesJavaConvention() {
        val u1 = OfflineUuid.fromUsername("Steve")
        val u2 = OfflineUuid.fromUsername("Steve")
        assertEquals(u1, u2)

        // Known vector: OfflinePlayer:Steve
        val expected = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".toByteArray(Charsets.UTF_8))
        assertEquals(expected, u1)
        assertEquals(expected.toString(), OfflineUuid.fromUsernameString("Steve"))
    }

    @Test
    fun differentNamesProduceDifferentUuids() {
        assertTrue(OfflineUuid.fromUsername("Steve") != OfflineUuid.fromUsername("Alex"))
    }

    @Test
    fun usernameValidation() {
        assertTrue(UsernameRules.isValid("Steve"))
        assertTrue(UsernameRules.isValid("Player_01"))
        assertTrue(UsernameRules.isValid("abc"))
        assertTrue(UsernameRules.isValid("a".repeat(16)))
        assertFalse(UsernameRules.isValid(""))
        assertFalse(UsernameRules.isValid("ab"))
        assertFalse(UsernameRules.isValid("a".repeat(17)))
        assertFalse(UsernameRules.isValid("bad name"))
        assertFalse(UsernameRules.isValid("bad-name"))
        assertFalse(UsernameRules.isValid("日本語"))
    }

    @Test
    fun createAndSelectOfflineAccount() = runBlocking {
        val dir = createTempDir()
        val repo = FileAccountRepository(File(dir, "accounts.json"))
        val mgr = AccountManager(repo)

        val result = mgr.createOfflineAccount("TestPlayer")
        assertTrue(result.isSuccess)
        val account = result.getOrThrow()
        assertEquals(AccountType.OFFLINE, account.type)
        assertEquals("TestPlayer", account.username)
        assertEquals(OfflineUuid.fromUsernameString("TestPlayer"), account.uuid)
        assertEquals(null, account.tokenRef)

        val selected = mgr.selected()
        assertEquals(account.id, selected?.id)

        // Duplicate rejected
        val dup = mgr.createOfflineAccount("testplayer")
        assertTrue(dup.isFailure)

        // Invalid rejected
        assertTrue(mgr.createOfflineAccount("ab").isFailure)
        assertTrue(mgr.createOfflineAccount("").isFailure)
    }

    @Test
    fun persistenceRoundTrip() = runBlocking {
        val dir = createTempDir()
        val file = File(dir, "accounts.json")
        val repo1 = FileAccountRepository(file)
        val mgr1 = AccountManager(repo1)
        val created = mgr1.createOfflineAccount("PersistMe").getOrThrow()

        val repo2 = FileAccountRepository(file)
        val mgr2 = AccountManager(repo2)
        val loaded = mgr2.selected()
        assertEquals(created.id, loaded?.id)
        assertEquals(created.uuid, loaded?.uuid)
        assertEquals(AccountType.OFFLINE, loaded?.type)
    }
}
