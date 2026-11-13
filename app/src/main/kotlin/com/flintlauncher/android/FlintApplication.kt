package com.flintlauncher.android

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.flintlauncher.android.download.DownloadConfig
import com.flintlauncher.android.download.DownloadManager
import com.flintlauncher.android.installer.GameLayout
import com.flintlauncher.android.installer.MinecraftInstaller
import com.flintlauncher.android.network.FlintHttp
import com.flintlauncher.android.partners.BundledServerFeed
import com.flintlauncher.android.partners.McStatusClient
import com.flintlauncher.android.partners.PartnersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import java.io.File

class FlintApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency wiring. Replaced by a DI framework only if the graph outgrows this. */
class AppContainer(private val app: Application) {
    val launcherVersion: String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private val http = FlintHttp.create(
        userAgent = "FlintLauncher/${launcherVersion.ifBlank { "dev" }} (Android)",
        cache = Cache(File(app.cacheDir, "http"), 20L * 1024 * 1024),
    )

    // Large downloads must not pass through the response cache.
    private val downloadHttp = http.newBuilder().cache(null).build()

    val partnersRepository = PartnersRepository(
        feed = BundledServerFeed,
        statusClient = McStatusClient(http),
    )

    // Game files can be over a gigabyte: prefer app-specific external storage, which is larger.
    private val gameRoot = File(app.getExternalFilesDir(null) ?: app.filesDir, "minecraft")
    private val layout = GameLayout(gameRoot)
    private val downloadManager = DownloadManager(downloadHttp, DownloadConfig(concurrency = 4))

    val installer = MinecraftInstaller(http = http, layout = layout, downloads = downloadManager)

    val installCoordinator = InstallCoordinator(
        runner = MinecraftInstallRunner(installer),
        controls = ManagerControls(downloadManager),
        pending = FilePendingInstallStore(File(app.filesDir, "pending-install.txt")),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    fun startInstallService() {
        ContextCompat.startForegroundService(app, Intent(app, InstallService::class.java))
    }
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(container.partnersRepository, container.launcherVersion)
        modelClass.isAssignableFrom(VersionsViewModel::class.java) ->
            VersionsViewModel(container.installer, container.installCoordinator, container::startInstallService)
        else -> throw IllegalArgumentException("Unknown ViewModel $modelClass")
    } as T
}
