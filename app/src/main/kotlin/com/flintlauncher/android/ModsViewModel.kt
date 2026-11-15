package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.mods.ModrinthFilters
import com.flintlauncher.android.mods.ModrinthProject
import com.flintlauncher.android.mods.ModrinthRepository
import com.flintlauncher.android.mods.ModrinthSideSupport
import com.flintlauncher.android.profiles.Loaders
import com.flintlauncher.android.profiles.Profile
import com.flintlauncher.android.profiles.ProfileRepository
import com.flintlauncher.android.ui.state.InstalledModUi
import com.flintlauncher.android.ui.state.Loadable
import com.flintlauncher.android.ui.state.ModFilterOption
import com.flintlauncher.android.ui.state.ModProjectUi
import com.flintlauncher.android.ui.state.ModVersionUi
import com.flintlauncher.android.ui.state.ModsProfileUi
import com.flintlauncher.android.ui.state.ModsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModsViewModel(
    private val mods: ModrinthRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ModsUiState())
    val state: StateFlow<ModsUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, results = Loadable.Loading, errorMessage = null)
            try {
                val profileList = profiles.list()
                val active = profiles.default() ?: profileList.firstOrNull()
                val tags = mods.tags()
                val categoryOptions = tags.categories.map { it.name.toOption() }
                val loaderOptions = tags.loaders.map { it.name.toOption() }
                val versionOptions = tags.gameVersions
                    .filter { it.versionType == "release" }
                    .map { it.version.toOption() }
                    .distinctBy { it.value }
                _state.value = _state.value.copy(
                    loading = false,
                    profiles = profileList.map { it.toUi() },
                    selectedProfileId = active?.id.orEmpty(),
                    categories = categoryOptions,
                    loaders = loaderOptions,
                    gameVersions = versionOptions,
                    selectedGameVersion = active?.minecraftVersion.orEmpty(),
                    selectedLoader = active?.loader?.takeUnless { it == Loaders.VANILLA }.orEmpty(),
                )
                loadInstalled()
                search()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    results = Loadable.Error("Could not load Modrinth. Check your connection and retry."),
                    errorMessage = e.message ?: "Modrinth request failed",
                )
            }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun setCategory(value: String) {
        _state.value = _state.value.copy(selectedCategory = value)
        search()
    }

    fun setGameVersion(value: String) {
        _state.value = _state.value.copy(selectedGameVersion = value)
        search()
    }

    fun setLoader(value: String) {
        _state.value = _state.value.copy(selectedLoader = value)
        search()
    }

    fun setClientSide(value: String) {
        _state.value = _state.value.copy(clientSide = value)
        search()
    }

    fun setServerSide(value: String) {
        _state.value = _state.value.copy(serverSide = value)
        search()
    }

    fun setSort(value: String) {
        if (value !in setOf("relevance", "downloads", "follows", "newest", "updated")) return
        _state.value = _state.value.copy(sortIndex = value)
        search()
    }

    fun search() {
        val current = _state.value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = current.copy(loading = true, errorMessage = null, offset = 0, results = Loadable.Loading)
            try {
                val response = mods.search(current.toFilters(offset = 0))
                val installedIds = _state.value.installed.mapTo(mutableSetOf()) { it.projectId }
                _state.value = _state.value.copy(
                    loading = false,
                    results = Loadable.Content(response.hits.map { it.toUi(installedIds) }),
                    totalHits = response.totalHits,
                    offset = response.offset,
                    pageLimit = response.limit,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    results = Loadable.Error(e.message ?: "Modrinth search failed"),
                )
            }
        }
    }

    fun refresh() = load()

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.offset + current.pageLimit >= current.totalHits) return
        viewModelScope.launch {
            _state.value = current.copy(loading = true, errorMessage = null)
            try {
                val nextOffset = current.offset + current.pageLimit
                val response = mods.search(current.toFilters(offset = nextOffset))
                val old = (current.results as? Loadable.Content)?.value.orEmpty()
                val installedIds = _state.value.installed.mapTo(mutableSetOf()) { it.projectId }
                _state.value = _state.value.copy(
                    loading = false,
                    results = Loadable.Content(old + response.hits.map { it.toUi(installedIds) }),
                    offset = response.offset,
                    totalHits = response.totalHits,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, notice = e.message ?: "Could not load more mods")
            }
        }
    }

    fun selectProfile(profileId: String) {
        val profile = profiles.get(profileId) ?: return
        try {
            profiles.setDefault(profileId)
            val profileList = profiles.list()
            _state.value = _state.value.copy(
                profiles = profileList.map { it.toUi() },
                selectedProfileId = profileId,
                selectedGameVersion = profile.minecraftVersion,
                selectedLoader = profile.loader.takeUnless { it == Loaders.VANILLA }.orEmpty(),
                selectedProject = null,
                selectedVersions = emptyList(),
            )
            loadInstalled()
            search()
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message ?: "Could not change profile")
        }
    }

    fun createProfile(name: String, minecraftVersion: String, loader: String) {
        if (name.isBlank() || minecraftVersion.isBlank() || loader !in Loaders.all || loader == Loaders.VANILLA) {
            _state.value = _state.value.copy(errorMessage = "Enter a profile name, Minecraft version, and supported mod loader.")
            return
        }
        try {
            val profile = profiles.create(
                Profile(
                    name = name.trim(),
                    minecraftVersion = minecraftVersion.trim(),
                    loader = loader,
                ),
            )
            profiles.setDefault(profile.id)
            _state.value = _state.value.copy(
                profiles = profiles.list().map { it.toUi() },
                selectedProfileId = profile.id,
                selectedGameVersion = profile.minecraftVersion,
                selectedLoader = profile.loader,
                errorMessage = null,
                notice = "Created profile ${profile.name}",
            )
            loadInstalled()
            search()
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message ?: "Could not create profile")
        }
    }

    fun selectProject(projectId: String) {
        val project = (_state.value.results as? Loadable.Content)?.value?.firstOrNull { it.id == projectId }
            ?: return
        _state.value = _state.value.copy(selectedProject = project, selectedVersions = emptyList(), detailLoading = true)
        val profile = selectedProfile()
        if (profile == null) {
            _state.value = _state.value.copy(detailLoading = false, notice = "Create or select a mod-loader profile to view compatible versions.")
            return
        }
        viewModelScope.launch {
            try {
                val versions = mods.versions(profile, projectId).map {
                    ModVersionUi(it.id, it.versionNumber, it.gameVersions, it.loaders, it.downloads, it.changelog)
                }
                _state.value = _state.value.copy(selectedVersions = versions, detailLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(detailLoading = false, notice = e.message ?: "Could not load mod versions")
            }
        }
    }

    fun closeProject() {
        _state.value = _state.value.copy(selectedProject = null, selectedVersions = emptyList(), detailLoading = false)
    }

    fun installVersion(projectId: String, versionId: String) {
        val profile = selectedProfile()
        val current = _state.value
        val project = (current.results as? Loadable.Content)?.value?.firstOrNull { it.id == projectId }
        val version = current.selectedVersions.firstOrNull { it.id == versionId }
        if (profile == null || project == null || version == null) {
            _state.value = current.copy(errorMessage = "Select a profile and compatible mod version first")
            return
        }
        val modProject = ModrinthProject(
            projectId = project.id,
            projectType = "mod",
            title = project.title,
            description = project.description,
            author = project.author,
            categories = project.categories,
        )
        _state.value = current.copy(busyProjectId = projectId, notice = null, errorMessage = null)
        viewModelScope.launch {
            try {
                val actualVersion = mods.versions(profile, projectId).firstOrNull { it.id == versionId }
                    ?: throw IllegalStateException("This version is no longer available for the selected profile")
                val installed = mods.install(profile, modProject, actualVersion)
                loadInstalled()
                _state.value = _state.value.copy(
                    busyProjectId = null,
                    notice = if (installed.isEmpty()) "This mod is already installed" else
                        "Installed ${installed.size} Modrinth mod(s), including required dependencies",
                )
                search()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(busyProjectId = null, errorMessage = e.message ?: "Mod installation failed")
            }
        }
    }

    fun uninstall(projectId: String) {
        val profile = selectedProfile() ?: return
        _state.value = _state.value.copy(busyProjectId = projectId, errorMessage = null)
        viewModelScope.launch {
            try {
                val removed = mods.uninstall(profile, projectId)
                loadInstalled()
                _state.value = _state.value.copy(
                    busyProjectId = null,
                    notice = if (removed) "Mod removed from this profile" else "Mod file was not found",
                )
                search()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(busyProjectId = null, errorMessage = e.message ?: "Uninstall failed")
            }
        }
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null, errorMessage = null)
    }

    private fun selectedProfile(): Profile? =
        _state.value.selectedProfileId.takeIf { it.isNotBlank() }?.let(profiles::get)

    private fun loadInstalled() {
        val profile = selectedProfile()
        if (profile == null) {
            _state.value = _state.value.copy(installed = emptyList())
            return
        }
        viewModelScope.launch {
            try {
                val installed = mods.installed(profile).map {
                    InstalledModUi(it.projectId, it.projectTitle, it.versionNumber, it.fileName)
                }
                _state.value = _state.value.copy(installed = installed)
                val installedIds = installed.mapTo(mutableSetOf()) { it.projectId }
                val old = (_state.value.results as? Loadable.Content)?.value
                if (old != null) {
                    _state.value = _state.value.copy(
                        results = Loadable.Content(old.map { it.copy(installed = it.id in installedIds) }),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = e.message ?: "Could not read installed mods")
            }
        }
    }

    private fun ModsUiState.toFilters(offset: Int): ModrinthFilters = ModrinthFilters(
        query = query,
        category = selectedCategory.takeIf { it.isNotBlank() },
        clientSide = clientSide.toSideSupport(),
        serverSide = serverSide.toSideSupport(),
        gameVersion = selectedGameVersion.takeIf { it.isNotBlank() },
        loader = selectedLoader.takeIf { it.isNotBlank() && it != Loaders.VANILLA },
        index = sortIndex,
        offset = offset,
        limit = pageLimit,
    )

    private fun String.toSideSupport(): ModrinthSideSupport? =
        ModrinthSideSupport.entries.firstOrNull { it.facetValue == this }

    private fun Profile.toUi() = ModsProfileUi(id, name, minecraftVersion, loader)

    private fun ModrinthProject.toUi(installedIds: Set<String>) = ModProjectUi(
        id = projectId,
        title = title,
        description = description,
        author = author,
        categories = displayCategories.ifEmpty { categories },
        clientSide = clientSide,
        serverSide = serverSide,
        downloads = downloads,
        installed = projectId in installedIds,
        iconUrl = iconUrl,
    )

    private fun String.toOption() = ModFilterOption(
        value = this,
        label = replace('-', ' ').replaceFirstChar { it.uppercase() },
    )
}
