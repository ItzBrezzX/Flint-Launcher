package com.flintlauncher.android.ui.state

data class ModFilterOption(val value: String, val label: String)

data class ModsProfileUi(
    val id: String,
    val name: String,
    val minecraftVersion: String,
    val loader: String,
)

data class ModProjectUi(
    val id: String,
    val title: String,
    val description: String,
    val author: String,
    val categories: List<String>,
    val clientSide: String,
    val serverSide: String,
    val downloads: Long,
    val installed: Boolean,
    val iconUrl: String? = null,
    val projectType: String = "mod",
)

data class ModVersionUi(
    val id: String,
    val versionNumber: String,
    val gameVersions: List<String>,
    val loaders: List<String>,
    val downloads: Long,
    val changelog: String?,
)

data class InstalledModUi(
    val projectId: String,
    val title: String,
    val versionNumber: String,
    val fileName: String,
)

data class ModsUiState(
    val loading: Boolean = false,
    val query: String = "",
    val categories: List<ModFilterOption> = emptyList(),
    val gameVersions: List<ModFilterOption> = emptyList(),
    val loaders: List<ModFilterOption> = emptyList(),
    val selectedCategory: String = "",
    val selectedProjectType: String = "all",
    val selectedGameVersion: String = "",
    val selectedLoader: String = "",
    val clientSide: String = "",
    val serverSide: String = "",
    val sortIndex: String = "downloads",
    val profiles: List<ModsProfileUi> = emptyList(),
    val selectedProfileId: String = "",
    val results: Loadable<List<ModProjectUi>> = Loadable.Loading,
    val installed: List<InstalledModUi> = emptyList(),
    val totalHits: Int = 0,
    val offset: Int = 0,
    val pageLimit: Int = 20,
    val selectedProject: ModProjectUi? = null,
    val selectedVersions: List<ModVersionUi> = emptyList(),
    val detailLoading: Boolean = false,
    val busyProjectId: String? = null,
    val errorMessage: String? = null,
    val notice: String? = null,
) {
    val selectedProfile: ModsProfileUi?
        get() = profiles.firstOrNull { it.id == selectedProfileId }
}

data class ModsActions(
    val onQueryChange: ((String) -> Unit)? = null,
    val onSearch: (() -> Unit)? = null,
    val onRefresh: (() -> Unit)? = null,
    val onCategoryChange: ((String) -> Unit)? = null,
    val onProjectTypeChange: ((String) -> Unit)? = null,
    val onGameVersionChange: ((String) -> Unit)? = null,
    val onLoaderChange: ((String) -> Unit)? = null,
    val onClientSideChange: ((String) -> Unit)? = null,
    val onServerSideChange: ((String) -> Unit)? = null,
    val onSortChange: ((String) -> Unit)? = null,
    val onSelectProfile: ((String) -> Unit)? = null,
    val onCreateProfile: ((String, String, String) -> Unit)? = null,
    val onSelectProject: ((String) -> Unit)? = null,
    val onCloseProject: (() -> Unit)? = null,
    val onInstallVersion: ((String, String) -> Unit)? = null,
    val onUninstall: ((String) -> Unit)? = null,
    val onLoadMore: (() -> Unit)? = null,
    val onDismissNotice: (() -> Unit)? = null,
)
