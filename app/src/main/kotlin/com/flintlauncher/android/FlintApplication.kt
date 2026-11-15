package com.flintlauncher.android

import android.app.Application
import android.os.Build
import android.os.Process
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.AndroidKeystoreTokenVault
import com.flintlauncher.android.account.FileAccountRepository
import com.flintlauncher.android.auth.MicrosoftAuthClient
import com.flintlauncher.android.download.DownloadConfig
import com.flintlauncher.android.download.DownloadManager
import com.flintlauncher.android.installer.GameLayout
import com.flintlauncher.android.installer.MinecraftInstaller
import com.flintlauncher.android.network.FlintHttp
import com.flintlauncher.android.mods.ModrinthApi
import com.flintlauncher.android.mods.ModrinthModInstaller
import com.flintlauncher.android.mods.ModrinthRepository
import com.flintlauncher.android.profiles.ProfileRepository
import com.flintlauncher.android.runtime.MobileJreManager
import com.flintlauncher.android.runtime.RuntimeArchitecture
import com.flintlauncher.android.partners.BundledServerFeed
import com.flintlauncher.android.partners.McStatusClient
import com.flintlauncher.android.partners.PartnersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import java.io.File
import java.io.IOException

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

    // Mod artifacts must stay on Modrinth's official CDN, including every redirected exchange.
    private val modDownloadHttp = http.newBuilder()
        .cache(null)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            if (url.scheme != "https" || url.host != "cdn.modrinth.com") {
                throw IOException("Blocked mod download outside cdn.modrinth.com")
            }
            chain.proceed(chain.request())
        }
        .build()

    val partnersRepository = PartnersRepository(
        feed = BundledServerFeed,
        statusClient = McStatusClient(http),
    )

    // Account metadata contains no credentials; Microsoft secrets are stored separately
    // in an AES-GCM vault whose key is protected by Android Keystore.
    private val accountRepository = FileAccountRepository(File(app.filesDir, "accounts.json"))
    val accountManager = AccountManager(accountRepository)
    val tokenVault = AndroidKeystoreTokenVault(File(app.filesDir, "account-tokens.enc"))
    val microsoftAccountStore = MicrosoftAccountStore(accountManager, tokenVault)

    private val lwjglComponents = LwjglComponentStore(app)

    /** Everything the JVM prints (and Java crash messages) land here. Shared with the launcher process. */
    val gameLogFile: File = File(app.getExternalFilesDir("logs") ?: File(app.filesDir, "logs"), "last-game.log")

    // Null when the build has no Azure client ID. The device-code flow needs no redirect URI;
    // the value below only satisfies the client's constructor for the browser (PKCE) flow.
    val microsoftAuthClient: MicrosoftAuthClient? = BuildConfig.MS_CLIENT_ID.takeIf { it.isNotBlank() }?.let { id ->
        MicrosoftAuthClient(
            http = http,
            clientId = id,
            redirectUri = "https://login.microsoftonline.com/common/oauth2/nativeclient",
        )
    }
    private val microsoftSessionProvider: MinecraftSessionProvider? = microsoftAuthClient?.let {
        MicrosoftSessionProvider(it, microsoftAccountStore)
    }

    // Game files can be over a gigabyte: prefer app-specific external storage, which is larger.
    private val gameRoot = File(app.getExternalFilesDir(null) ?: app.filesDir, "minecraft")
    private val layout = GameLayout(gameRoot)
    private val downloadManager = DownloadManager(downloadHttp, DownloadConfig(concurrency = 4))
    private val runtimeDownloadManager = DownloadManager(downloadHttp, DownloadConfig(concurrency = 1, maxAttempts = 4))
    private val modDownloadManager = DownloadManager(modDownloadHttp, DownloadConfig(concurrency = 2))

    val deviceModel: String = Build.MODEL.orEmpty()
    val androidVersion: String = Build.VERSION.RELEASE.orEmpty()
    val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()
    val totalMemoryMb: Long = runCatching {
        File("/proc/meminfo").useLines { lines ->
            lines.firstOrNull { it.startsWith("MemTotal:") }
                ?.substringAfter(":")
                ?.trim()
                ?.substringBefore(" ")
                ?.toLongOrNull()
                ?.div(1024)
                ?: 0L
        }
    }.getOrDefault(0L)

    /** ABI for this app process (different from the set of ABIs supported by the CPU). */
    val deviceProcessArch: String? = RuntimeArchitecture.fromSupportedAbis(
        supportedAbis,
        Process.is64Bit(),
    )
    val mobileJreManager: MobileJreManager? = deviceProcessArch?.let { arch ->
        MobileJreManager(
            runtimesDir = File(app.filesDir, "runtimes"),
            downloadDir = File(app.cacheDir, "jre-downloads"),
            deviceArch = arch,
            downloads = runtimeDownloadManager,
        )
    }

    val profileRepository = ProfileRepository(File(app.filesDir, "profiles.json"))
    val modrinthRepository = ModrinthRepository(
        api = ModrinthApi(http),
        installer = ModrinthModInstaller(modDownloadManager, gameRoot),
    )

    val installer = MinecraftInstaller(http = http, layout = layout, downloads = downloadManager)

    // The PLAY screen and game launcher share the same installer, account selection,
    // verified mobile JRE manager, game root and APK-native library directory.
    val playLaunchCoordinator = PlayLaunchCoordinator(
        installer = installer,
        accounts = accountManager,
        runtimeManager = mobileJreManager,
        deviceArch = deviceProcessArch,
        gameRoot = gameRoot,
        cacheDir = app.cacheDir,
        nativeLibraryDir = File(app.applicationInfo.nativeLibraryDir),
        launcherVersion = launcherVersion,
        microsoftSessions = microsoftSessionProvider,
        androidLwjglJars = { lwjglComponents.jars() },
        gameLogFile = gameLogFile,
    )

    val installCoordinator = InstallCoordinator(
        runner = MinecraftInstallRunner(installer),
        controls = ManagerControls(downloadManager),
        pending = FilePendingInstallStore(File(app.filesDir, "pending-install.txt")),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    fun startGameActivity(versionId: String) {
        app.startActivity(
            Intent(app, FlintGameActivity::class.java)
                .putExtra(FlintGameActivity.EXTRA_VERSION_ID, versionId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun startInstallService() {
        ContextCompat.startForegroundService(app, Intent(app, InstallService::class.java))
    }
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(
                container.partnersRepository,
                container.installer,
                container.accountManager,
                container.playLaunchCoordinator,
                container.launcherVersion,
                container::startGameActivity,
            )
        modelClass.isAssignableFrom(VersionsViewModel::class.java) ->
            VersionsViewModel(container.installer, container.installCoordinator, container::startInstallService)
        modelClass.isAssignableFrom(ModsViewModel::class.java) ->
            ModsViewModel(container.modrinthRepository, container.profileRepository)
        modelClass.isAssignableFrom(AccountsViewModel::class.java) ->
            AccountsViewModel(container.accountManager, container.microsoftAccountStore, container.microsoftAuthClient)
        modelClass.isAssignableFrom(RuntimeSettingsViewModel::class.java) ->
            RuntimeSettingsViewModel(
                container.mobileJreManager,
                container.deviceProcessArch,
                container.deviceModel,
                container.androidVersion,
                container.supportedAbis,
                container.totalMemoryMb,
            )
        else -> throw IllegalArgumentException("Unknown ViewModel $modelClass")
    } as T
}
