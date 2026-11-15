package com.flintlauncher.android.friends

/**
 * Friends presence. Explicitly unavailable in Spike 0: no friends backend is
 * configured, and online status is never faked. A future optional backend would
 * implement [FriendsRepository] with real presence data.
 */
sealed interface FriendsAvailability {
    data object Unavailable : FriendsAvailability
    data class Available(val friends: List<FriendPresence>) : FriendsAvailability
}

data class FriendPresence(
    val username: String,
    val online: Boolean,
    val currentServer: String? = null,
)

interface FriendsRepository {
    suspend fun load(): FriendsAvailability
}

/** Honest stub: always reports unavailable. Never fabricates online friends. */
object UnavailableFriendsRepository : FriendsRepository {
    override suspend fun load(): FriendsAvailability = FriendsAvailability.Unavailable
}
