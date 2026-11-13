package com.flintlauncher.android.ui.state

enum class AccountKind { MICROSOFT, OFFLINE }

data class AccountUi(val username: String, val kind: AccountKind)

/** One installation/profile as shown in the UI. */
data class InstallationUi(
    val id: String,
    val name: String,
    val minecraftVersion: String,
    val loader: String,
    val javaMajor: Int,
) {
    val summary: String get() = "$minecraftVersion \u00B7 $loader \u00B7 Java $javaMajor"
}

data class NewsCardUi(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val dateLabel: String,
)

enum class ServerEdition { JAVA, BEDROCK }

/** Never guess: a server is Unknown until a real status query has answered. */
sealed interface ServerStatus {
    data object Unknown : ServerStatus
    data object Offline : ServerStatus
    data class Online(val players: Int?) : ServerStatus
}

data class PartnerServerUi(
    val name: String,
    val address: String,
    val edition: ServerEdition,
    val status: ServerStatus,
    /** https URL opened by Visit; null disables Visit. */
    val website: String? = null,
)

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Error(val message: String) : Loadable<Nothing>
    data class Content<T>(val value: T) : Loadable<T>
}

sealed interface FriendsUi {
    /** No backend configured. Presence is never faked. */
    data object Unavailable : FriendsUi
    data class Available(val names: List<String>) : FriendsUi
}

data class HomeUiState(
    val account: AccountUi? = null,
    val installations: List<InstallationUi> = emptyList(),
    val selectedInstallationId: String? = null,
    val launchMessage: String? = null,
    val news: Loadable<List<NewsCardUi>> = Loadable.Content(emptyList()),
    val partners: Loadable<List<PartnerServerUi>> = Loadable.Content(emptyList()),
    val friends: FriendsUi = FriendsUi.Unavailable,
    val launcherVersion: String = "",
) {
    val selected: InstallationUi?
        get() = installations.firstOrNull { it.id == selectedInstallationId }
}

/**
 * A null callback means "this capability does not exist yet": the matching
 * control is shown disabled instead of doing nothing when tapped.
 */
data class HomeActions(
    val onLaunch: (() -> Unit)? = null,
    val onSelectInstallation: ((String) -> Unit)? = null,
    val onDismissLaunchMessage: (() -> Unit)? = null,
    val onAddProfile: (() -> Unit)? = null,
    val onEditProfile: ((String) -> Unit)? = null,
    val onDeleteProfile: ((String) -> Unit)? = null,
    val onOpenAccounts: (() -> Unit)? = null,
    val onOpenNews: ((String) -> Unit)? = null,
    val onRetryNews: (() -> Unit)? = null,
    val onCopyAddress: ((String) -> Unit)? = null,
    val onRefreshPartners: (() -> Unit)? = null,
    /** Receives the server's https website URL. */
    val onVisitServer: ((String) -> Unit)? = null,
)
