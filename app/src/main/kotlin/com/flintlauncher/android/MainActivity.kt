package com.flintlauncher.android

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flintlauncher.android.ui.home.HomeScreen
import com.flintlauncher.android.ui.mods.ModsScreen
import com.flintlauncher.android.ui.settings.RuntimeSettingsScreen
import com.flintlauncher.android.ui.nav.Destination
import com.flintlauncher.android.ui.nav.FlintScaffold
import com.flintlauncher.android.ui.nav.NotImplementedScreen
import com.flintlauncher.android.ui.state.HomeActions
import com.flintlauncher.android.ui.state.ModsActions
import com.flintlauncher.android.ui.state.RuntimeSettingsActions
import com.flintlauncher.android.ui.state.VersionsActions
import com.flintlauncher.android.ui.theme.FlintTheme
import com.flintlauncher.android.ui.versions.InstallationsScreen

class MainActivity : ComponentActivity() {

    private val factory get() = AppViewModelFactory((application as FlintApplication).container)
    private val homeViewModel: HomeViewModel by viewModels { factory }
    private val versionsViewModel: VersionsViewModel by viewModels { factory }
    private val modsViewModel: ModsViewModel by viewModels { factory }
    private val runtimeSettingsViewModel: RuntimeSettingsViewModel by viewModels { factory }

    // The install works without the notification permission; this only controls the progress notification.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FlintTheme {
                var destination by rememberSaveable { mutableStateOf(Destination.PLAY) }
                val homeState by homeViewModel.state.collectAsStateWithLifecycle()
                val versionsState by versionsViewModel.state.collectAsStateWithLifecycle()
                val modsState by modsViewModel.state.collectAsStateWithLifecycle()
                val runtimeSettingsState by runtimeSettingsViewModel.state.collectAsStateWithLifecycle()

                // Only capabilities that really exist are wired. Every other action stays
                // null, which the UI renders as a disabled control.
                val homeActions = remember {
                    HomeActions(
                        onLaunch = { homeViewModel.launchSelected() },
                        onSelectInstallation = { homeViewModel.selectInstallation(it) },
                        onDismissLaunchMessage = { homeViewModel.dismissLaunchMessage() },
                        onRefreshPartners = { homeViewModel.refreshPartners(force = true) },
                        onCopyAddress = { address -> copyAddress(address) },
                        onVisitServer = { url -> openWebsite(url) },
                    )
                }

                val versionsActions = remember {
                    VersionsActions(
                        onQueryChange = { versionsViewModel.setQuery(it) },
                        onFilterChange = { versionsViewModel.setFilter(it) },
                        onToggleSort = { versionsViewModel.toggleSort() },
                        onInstall = { id ->
                            requestNotificationPermissionIfNeeded()
                            versionsViewModel.install(id)
                        },
                        onPause = { versionsViewModel.pause() },
                        onResume = { versionsViewModel.resume() },
                        onCancel = { versionsViewModel.cancel() },
                        onRetryCatalog = { versionsViewModel.loadCatalog() },
                        onDismissResult = { versionsViewModel.dismissResult() },
                    )
                }

                val runtimeSettingsActions = remember {
                    RuntimeSettingsActions(
                        onRefresh = { runtimeSettingsViewModel.refresh() },
                        onInstallJava = { runtimeSettingsViewModel.installJava(it) },
                        onDismissMessage = { runtimeSettingsViewModel.dismissMessage() },
                    )
                }

                val modsActions = remember {
                    ModsActions(
                        onQueryChange = { modsViewModel.setQuery(it) },
                        onSearch = { modsViewModel.search() },
                        onRefresh = { modsViewModel.refresh() },
                        onCategoryChange = { modsViewModel.setCategory(it) },
                        onProjectTypeChange = { modsViewModel.setProjectType(it) },
                        onGameVersionChange = { modsViewModel.setGameVersion(it) },
                        onLoaderChange = { modsViewModel.setLoader(it) },
                        onClientSideChange = { modsViewModel.setClientSide(it) },
                        onServerSideChange = { modsViewModel.setServerSide(it) },
                        onSortChange = { modsViewModel.setSort(it) },
                        onSelectProfile = { modsViewModel.selectProfile(it) },
                        onCreateProfile = { name, version, loader -> modsViewModel.createProfile(name, version, loader) },
                        onSelectProject = { modsViewModel.selectProject(it) },
                        onCloseProject = { modsViewModel.closeProject() },
                        onInstallVersion = { projectId, versionId -> modsViewModel.installVersion(projectId, versionId) },
                        onUninstall = { modsViewModel.uninstall(it) },
                        onLoadMore = { modsViewModel.loadMore() },
                        onDismissNotice = { modsViewModel.dismissNotice() },
                    )
                }

                FlintScaffold(
                    selected = destination,
                    onSelect = {
                        destination = it
                        if (it == Destination.PLAY) homeViewModel.refreshInstallations()
                    },
                ) {
                    when (destination) {
                        Destination.PLAY -> HomeScreen(state = homeState, actions = homeActions)
                        Destination.PROFILES -> InstallationsScreen(state = versionsState, actions = versionsActions)
                        Destination.MODS -> ModsScreen(state = modsState, actions = modsActions)
                        Destination.SETTINGS -> RuntimeSettingsScreen(
                            state = runtimeSettingsState,
                            actions = runtimeSettingsActions,
                        )
                        else -> NotImplementedScreen(destination.label)
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun copyAddress(address: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Server address", address))
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.address_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWebsite(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
        }
    }
}
