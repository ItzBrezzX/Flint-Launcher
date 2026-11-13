package com.flintlauncher.android.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.flintlauncher.android.ui.state.AccountKind
import com.flintlauncher.android.ui.state.AccountUi
import com.flintlauncher.android.ui.state.HomeActions
import com.flintlauncher.android.ui.state.HomeUiState
import com.flintlauncher.android.ui.state.InstallationUi
import com.flintlauncher.android.ui.state.Loadable
import com.flintlauncher.android.ui.state.NewsCardUi
import com.flintlauncher.android.ui.state.PartnerServerUi
import com.flintlauncher.android.ui.state.ServerEdition
import com.flintlauncher.android.ui.state.ServerStatus
import com.flintlauncher.android.ui.theme.FlintTheme

// PREVIEW-ONLY sample data for Android Studio. The app never uses it at runtime.
private val previewState = HomeUiState(
    account = AccountUi("PreviewPlayer", AccountKind.OFFLINE),
    installations = listOf(
        InstallationUi("1", "Flint Fabric", "1.21.11", "Fabric", 21),
        InstallationUi("2", "Vanilla", "1.21.11", "Vanilla", 21),
    ),
    selectedInstallationId = "1",
    news = Loadable.Content(
        listOf(NewsCardUi("n1", "Launcher", "Sample headline", "Sample summary text for layout only.", "1 Jan 2026")),
    ),
    partners = Loadable.Content(
        listOf(PartnerServerUi("Sample Server", "play.example.invalid", ServerEdition.JAVA, ServerStatus.Unknown)),
    ),
    launcherVersion = "0.1.0-dev",
)

@Composable
private fun PreviewHome(state: HomeUiState) {
    FlintTheme { HomeScreen(state = state, actions = HomeActions()) }
}

@Preview(name = "Phone", widthDp = 360, heightDp = 800)
@Composable
private fun HomePhonePreview() = PreviewHome(previewState)

@Preview(name = "Phone, empty", widthDp = 360, heightDp = 800)
@Composable
private fun HomeEmptyPreview() = PreviewHome(HomeUiState())

@Preview(name = "Tablet landscape", widthDp = 960, heightDp = 600)
@Composable
private fun HomeTabletPreview() = PreviewHome(previewState)
