package com.flintlauncher.android.account

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Persistent account store.
 *
 * Offline accounts are stored in full (no secrets).
 * Microsoft accounts store only metadata + tokenRef; actual tokens live in a Keystore-backed store.
 *
 * This implementation uses a JSON file for portability and unit testing.
 * Production wiring should prefer DataStore / Room with the same interface.
 */
interface AccountRepository {
    val accounts: Flow<List<FlintAccount>>
    val selectedId: Flow<String?>

    suspend fun list(): List<FlintAccount>
    suspend fun get(id: String): FlintAccount?
    suspend fun selected(): FlintAccount?
    suspend fun add(account: FlintAccount)
    suspend fun remove(id: String)
    suspend fun select(id: String?)
    suspend fun touch(id: String)
}

@Serializable
private data class StoredAccount(
    val id: String,
    val type: String,
    val username: String,
    val uuid: String,
    val tokenRef: String? = null,
    val skinUrl: String? = null,
    val createdAtMillis: Long,
    val lastUsedMillis: Long,
)

@Serializable
private data class AccountFile(
    val selectedId: String? = null,
    val accounts: List<StoredAccount> = emptyList(),
)

class FileAccountRepository(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = false },
) : AccountRepository {
    private val _accounts = MutableStateFlow<List<FlintAccount>>(emptyList())
    private val _selectedId = MutableStateFlow<String?>(null)
    private val persistenceLock = Any()

    override val accounts: Flow<List<FlintAccount>> = _accounts.asStateFlow()
    override val selectedId: Flow<String?> = _selectedId.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        if (!file.isFile) {
            _accounts.value = emptyList()
            _selectedId.value = null
            return
        }
        runCatching {
            val parsed = json.decodeFromString(AccountFile.serializer(), file.readText())
            _accounts.value = parsed.accounts.map { it.toDomain() }
            _selectedId.value = parsed.selectedId
        }.onFailure {
            _accounts.value = emptyList()
            _selectedId.value = null
        }
    }

    /**
     * Replace the account index atomically. A failed rename must never be reported as a
     * successful save: otherwise the UI can show an account that disappears on restart.
     * The temporary file is kept beside the destination so the move stays on one filesystem.
     */
    private fun persist() = synchronized(persistenceLock) {
        val parent = file.absoluteFile.parentFile
        check(parent == null || (parent.isDirectory || parent.mkdirs())) {
            "Could not create account storage directory"
        }

        val payload = AccountFile(
            selectedId = _selectedId.value,
            accounts = _accounts.value.map { it.toStored() },
        )
        val temporary = File(file.absolutePath + ".tmp")
        try {
            temporary.writeText(json.encodeToString(AccountFile.serializer(), payload))
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            // A failed write/move leaves the previous account file intact.
            if (temporary.exists()) temporary.delete()
        }
    }

    override suspend fun list(): List<FlintAccount> = _accounts.value

    override suspend fun get(id: String): FlintAccount? = _accounts.value.find { it.id == id }

    override suspend fun selected(): FlintAccount? {
        val id = _selectedId.value ?: return null
        return get(id)
    }

    override suspend fun add(account: FlintAccount) {
        _accounts.update { current ->
            val withoutDupName = current.filterNot {
                it.type == account.type && it.username.equals(account.username, ignoreCase = true)
            }
            withoutDupName + account
        }
        if (_selectedId.value == null) {
            _selectedId.value = account.id
        }
        persist()
    }

    override suspend fun remove(id: String) {
        _accounts.update { it.filterNot { a -> a.id == id } }
        if (_selectedId.value == id) {
            _selectedId.value = _accounts.value.firstOrNull()?.id
        }
        persist()
    }

    override suspend fun select(id: String?) {
        if (id != null && _accounts.value.none { it.id == id }) return
        _selectedId.value = id
        persist()
    }

    override suspend fun touch(id: String) {
        val now = System.currentTimeMillis()
        _accounts.update { list ->
            list.map { if (it.id == id) it.copy(lastUsedMillis = now) else it }
        }
        persist()
    }

    private fun StoredAccount.toDomain() = FlintAccount(
        id = id,
        type = AccountType.valueOf(type),
        username = username,
        uuid = uuid,
        tokenRef = tokenRef,
        skinUrl = skinUrl,
        createdAtMillis = createdAtMillis,
        lastUsedMillis = lastUsedMillis,
    )

    private fun FlintAccount.toStored() = StoredAccount(
        id = id,
        type = type.name,
        username = username,
        uuid = uuid,
        tokenRef = tokenRef,
        skinUrl = skinUrl,
        createdAtMillis = createdAtMillis,
        lastUsedMillis = lastUsedMillis,
    )
}

/**
 * High-level account operations used by UI and launcher.
 */
class AccountManager(
    private val repository: AccountRepository,
) {
    val accounts = repository.accounts
    val selectedId = repository.selectedId

    suspend fun createOfflineAccount(rawUsername: String): Result<FlintAccount> {
        val username = UsernameRules.validateOrNull(rawUsername)
            ?: return Result.failure(IllegalArgumentException(UsernameRules.HINT))

        val existing = repository.list().any {
            it.type == AccountType.OFFLINE && it.username.equals(username, ignoreCase = true)
        }
        if (existing) {
            return Result.failure(IllegalArgumentException("An offline account named \"$username\" already exists"))
        }

        val account = FlintAccount(
            id = UUID.randomUUID().toString(),
            type = AccountType.OFFLINE,
            username = username,
            uuid = OfflineUuid.fromUsernameString(username),
            tokenRef = null,
        )
        repository.add(account)
        repository.select(account.id)
        return Result.success(account)
    }

    suspend fun addMicrosoftAccount(account: FlintAccount): Result<FlintAccount> {
        require(account.type == AccountType.MICROSOFT)
        repository.add(account)
        repository.select(account.id)
        return Result.success(account)
    }

    suspend fun remove(id: String) = repository.remove(id)
    suspend fun select(id: String) = repository.select(id)
    suspend fun selected(): FlintAccount? = repository.selected()
    suspend fun list(): List<FlintAccount> = repository.list()
}
