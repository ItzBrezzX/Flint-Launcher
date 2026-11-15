package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.partners.PartnersRepository
import com.flintlauncher.android.installer.MinecraftInstaller
import com.flintlauncher.android.minecraft.version.Environment
import com.flintlauncher.android.minecraft.version.LaunchCommandBuilder
import com.flintlauncher.android.minecraft.version.LaunchContext
import com.flintlauncher.android.minecraft.version.VersionJsonParser
import com.flintlauncher.android.profiles.Profile
import com.flintlauncher.android.profiles.ProfileDirs
import com.flintlauncher.android.profiles.ProfileRepository
import com.flintlauncher.android.runtime.GameLauncher
import com.flintlauncher.android.runtime.KnownJreArtifacts
import com.flintlauncher.android.runtime.LaunchRequest
import com.flintlauncher.android.runtime.LaunchResult
import com.flintlauncher.android.runtime.MobileJreManager
import com.flintlauncher.android.runtime.RendererInfo
import com.flintlauncher.android.runtime.RendererKind
import com.flintlauncher.android.ui.state.AccountKind
import com.flintlauncher.android.ui.state.AccountUi
import com.flintlauncher.android.ui.state.HomeUiState
import com.flintlauncher.android.ui.state.Loadable
import com.flintlauncher.android.ui.state.PartnerServerUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(
    private val partners: PartnersRepository,
    private val installer: MinecraftInstaller,
    private val runtimeManager: MobileJreManager?,
    private val deviceArch: String?,
    private val launcherVersion: String,
    private val accountManager: AccountManager,
    private val profileRepository: ProfileRepository,
    private val gameLauncher: GameLauncher?,
    private val gameRoot: File,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(launcherVersion = launcherVersion, partners = Loadable.Loading),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var installationJob: Job? = null
    private var launchJob: Job? = null

    init {
        refreshInstallations()
        refreshPartners(force = false)
    }

    fun refreshInstallations() {
        installationJob?.cancel()
        installationJob = viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                installer.installedVersions().map { version ->
                    com.flintlauncher.android.ui.state.InstallationUi(
                        id = version.versionId,
                        name = "Minecraft ${version.versionId}",
                        minecraftVersion = version.versionId,
                        loader = "vanilla",
                        javaMajor = version.requiredJavaMajor,
                    )
                }
            }
            _state.update { current ->
                val selectedId = current.selectedInstallationId
                    ?.takeIf { id -> installed.any { it.id == id } }
                    ?: installed.firstOrNull()?.id
                current.copy(installations = installed, selectedInstallationId = selectedId)
            }
        }
    }

    fun selectInstallation(id: String) {
        if (_state.value.installations.none { it.id == id }) return
        launchJob?.cancel()
        _state.update { it.copy(selectedInstallationId = id, launchMessage = null) }
    }

    /**
     * Builds the complete command from the installed version JSON, account and profile,
     * then hands that exact classpath/argument set to the native game launcher.
     */
    fun launchSelected() {
        val selected = _state.value.selected
        if (selected == null) {
            _state.update { it.copy(launchMessage = "Install a Minecraft version first.") }
            return
        }
        if (launchJob?.isActive == true) return

        launchJob = viewModelScope.launch {
            _state.update { it.copy(launchMessage = "Preparing Minecraft classpath and launch arguments…") }

            val account = try {
                accountManager.selected() ?: run {
                    val existingOffline = accountManager.list().firstOrNull { it.type == AccountType.OFFLINE }
                    if (existingOffline != null) {
                        accountManager.select(existingOffline.id)
                        existingOffline
                    } else {
                        accountManager.createOfflineAccount("FlintPlayer").getOrElse { error ->
                            _state.update { it.copy(launchMessage = "Could not create an offline account: ${error.message}") }
                            return@launch
                        }
                    }
                }
            } catch (error: Exception) {
                _state.update { it.copy(launchMessage = "Could not load the selected account: ${error.message}") }
                return@launch
            }

            _state.update {
                it.copy(
                    account = AccountUi(
                        username = account.username,
                        kind = if (account.type == AccountType.OFFLINE) AccountKind.OFFLINE else AccountKind.MICROSOFT,
                    ),
                )
            }
            if (account.type == AccountType.MICROSOFT) {
                _state.update {
                    it.copy(launchMessage = "Microsoft account tokens are not connected to the JVM launch path yet. Select an offline account for local testing.")
                }
                return@launch
            }

            val profiles = profileRepository.list()
            val profile = profiles.firstOrNull {
                it.enabled && it.minecraftVersion == selected.minecraftVersion
            } ?: try {
                val baseName = "Minecraft ${selected.minecraftVersion}"
                val name = if (profiles.none { it.name.equals(baseName, ignoreCase = true) }) {
                    baseName
                } else {
                    "$baseName ${profiles.size + 1}"
                }
                profileRepository.create(
                    Profile(
                        name = name,
                        minecraftVersion = selected.minecraftVersion,
                    ),
                )
            } catch (error: Exception) {
                _state.update { it.copy(launchMessage = "Could not load or create a game profile: ${error.message}") }
                return@launch
            }

            val profileDirs = try {
                ProfileDirs(gameRoot, profile.id).also { dirs ->
                    withContext(Dispatchers.IO) { dirs.createAll() }
                }
            } catch (error: Exception) {
                _state.update { it.copy(launchMessage = "Invalid game profile directory: ${error.message}") }
                return@launch
            }

            val installed = try {
                withContext(Dispatchers.IO) {
                    installer.loadInstalled(selected.minecraftVersion, File(profileDirs.gameDir, "natives"))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val detail = error.message ?: "unknown installation error"
                _state.update {
                    it.copy(launchMessage = "Minecraft ${selected.minecraftVersion} is not launch-ready: $detail. Repair this installation first.")
                }
                return@launch
            }

            val manager = runtimeManager
            val arch = deviceArch
            if (manager == null || arch == null) {
                _state.update {
                    it.copy(launchMessage = "This APK process ABI is not supported by Flint's mobile Java runtime catalog.")
                }
                return@launch
            }

            val compatible = try {
                manager.findCompatible(installed.requiredJavaMajor, arch)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(launchMessage = "Could not check Java runtimes: ${error.message ?: "unknown error"}") }
                return@launch
            }

            val runtime = if (compatible != null) {
                compatible
            } else {
                val targetMajor = KnownJreArtifacts.compatibleMajor(installed.requiredJavaMajor, arch)
                if (targetMajor == null) {
                    _state.update {
                        it.copy(launchMessage = "No published mobile Java runtime for ABI $arch satisfies Java ${installed.requiredJavaMajor}.")
                    }
                    return@launch
                }
                _state.update {
                    it.copy(launchMessage = "Downloading Java $targetMajor for ${selected.minecraftVersion}…")
                }
                val result = try {
                    manager.install(targetMajor)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _state.update { it.copy(launchMessage = "Java download failed: ${error.message ?: "unknown error"}") }
                    return@launch
                }
                result.getOrElse { error ->
                    _state.update {
                        it.copy(launchMessage = "Java $targetMajor could not be installed: ${error.message ?: "unknown error"}")
                    }
                    return@launch
                }
            }

            val verified = try {
                manager.verify(runtime)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!verified) {
                _state.update { it.copy(launchMessage = "Java ${runtime.majorVersion} was installed but failed runtime verification.") }
                return@launch
            }

            val commandAndRequest = try {
                withContext(Dispatchers.IO) {
                    val assetsRoot = installed.assetsRoot
                    val indexFile = File(assetsRoot, "indexes/${installed.assetIndexId}.json")
                    val assetIndex = VersionJsonParser.parseAssetIndex(indexFile.readText())
                    val gameAssets = when {
                        assetIndex.mapToResources -> File(gameRoot, "resources")
                        assetIndex.virtual -> File(assetsRoot, "virtual/legacy")
                        else -> assetsRoot
                    }
                    val classpath = installed.classpath.joinToString(File.pathSeparator) { it.absolutePath }
                    val context = LaunchContext(
                        playerName = account.username,
                        uuid = account.uuid,
                        accessToken = "0",
                        userType = "legacy",
                        versionName = installed.version.id,
                        versionType = installed.version.type.name.lowercase(),
                        gameDirectory = profileDirs.gameDir.absolutePath,
                        assetsRoot = assetsRoot.absolutePath,
                        assetsIndexName = installed.assetIndexId,
                        nativesDirectory = File(profileDirs.gameDir, "natives").absolutePath,
                        libraryDirectory = installer.librariesDirectory.absolutePath,
                        classpath = classpath,
                        launcherVersion = launcherVersion,
                        launcherName = "Flint",
                        gameAssets = gameAssets.absolutePath,
                    )
                    val extraJvm = buildList {
                        add("-Xms256M")
                        add("-Xmx${profile.memoryMb}M")
                        installed.loggingJvmArgument?.let(::add)
                        addAll(profile.jvmArguments)
                    }
                    val command = LaunchCommandBuilder.build(
                        version = installed.version,
                        context = context,
                        environment = Environment(
                            osName = "linux",
                            osArch = runtime.arch,
                        ),
                        extraJvmArguments = extraJvm,
                        extraGameArguments = profile.gameArguments,
                    )
                    val nativeDirectory = File(profileDirs.gameDir, "natives")
                    val jvmArguments = command.jvmArguments.toMutableList().apply {
                        if (none { it.startsWith("-Djava.library.path=") }) {
                            add(0, "-Djava.library.path=${nativeDirectory.absolutePath}")
                        }
                        if (none { it.startsWith("-Dorg.lwjgl.librarypath=") }) {
                            add(0, "-Dorg.lwjgl.librarypath=${nativeDirectory.absolutePath}")
                        }
                    }
                    val request = LaunchRequest(
                        versionId = installed.version.id,
                        gameDir = profileDirs.gameDir,
                        assetsDir = assetsRoot,
                        classpath = installed.classpath,
                        nativeDir = nativeDirectory,
                        mainClass = command.mainClass,
                        jvmArgs = jvmArguments,
                        gameArgs = command.gameArguments,
                        accountUsername = account.username,
                        accountUuid = account.uuid,
                        accessToken = "0",
                        userType = "legacy",
                        runtime = runtime,
                        renderer = RendererInfo(
                            id = "renderer-unavailable",
                            kind = RendererKind.UNKNOWN,
                            displayName = "No Android Minecraft renderer bundled",
                            available = false,
                            notes = "The GL4ES / Android window bridge is not ready yet",
                        ),
                    )
                    request
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(launchMessage = "Could not build Minecraft JVM/game arguments: ${error.message ?: "invalid version metadata"}")
                }
                return@launch
            }

            val launcher = gameLauncher
            if (launcher == null) {
                _state.update { it.copy(launchMessage = "The native Java game launcher is unavailable in this APK.") }
                return@launch
            }

            _state.update { it.copy(launchMessage = "Classpath and Minecraft arguments are ready; checking renderer and native launch bridge…") }
            val result = try {
                launcher.launch(commandAndRequest)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LaunchResult.Failed("Game launch failed: ${error.message ?: "unknown error"}")
            }

            val message = when (result) {
                is LaunchResult.Started -> "Minecraft JVM session started."
                is LaunchResult.Failed -> result.reason
                LaunchResult.BlockedMissingRuntime -> "The selected Java runtime is not available."
                LaunchResult.BlockedMissingRenderer -> "Minecraft classpath and arguments are connected, but PLAY is blocked until GL4ES/OpenGL and the Android game window bridge are bundled."
                LaunchResult.BlockedNotReady -> "Minecraft installation is not ready to launch."
            }
            _state.update { it.copy(launchMessage = message) }
        }
    }

    fun dismissLaunchMessage() {
        _state.update { it.copy(launchMessage = null) }
    }

    fun refreshPartners(force: Boolean = true) {
        viewModelScope.launch {
            // Keep showing the current list while refreshing; only show the spinner when there is none.
            _state.update { if (it.partners is Loadable.Content) it else it.copy(partners = Loadable.Loading) }
            val loaded: Loadable<List<PartnerServerUi>> = try {
                Loadable.Content(partners.load(forceRefresh = force).map { it.toUi() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Loadable.Error("Could not load the server list.")
            }
            _state.update { it.copy(partners = loaded) }
        }
    }
}
