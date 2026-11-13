pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Flint-Launcher"

include(
    ":app",
    ":core",
    ":auth",
    ":account",
    ":minecraft",
    ":runtime",
    ":installer",
    ":download",
    ":profiles",
    ":mods",
    ":modpacks",
    ":news",
    ":partners",
    ":friends",
    ":network",
    ":database",
    ":ui",
    ":native",
)
