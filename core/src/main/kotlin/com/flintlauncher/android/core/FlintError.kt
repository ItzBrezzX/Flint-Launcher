package com.flintlauncher.android.core

/**
 * Categorized, user-actionable errors shared by every logic module. The category drives
 * the launcher's error UI ("install a Java runtime", "retry the download", "log in again")
 * and keeps ad-hoc string matching out of call sites.
 *
 * These are logic-layer errors only. Android-layer errors (StorageAccessFramework,
 * permissions) stay in the app module and are mapped into one of these before crossing.
 */
sealed interface FlintError {
    val message: String

    /** No Java runtime of the required major version is installed and none can be fetched. */
    data class MissingRuntime(override val message: String, val requiredJavaMajor: Int) : FlintError

    /** A file download failed permanently (4xx, checksum mismatch, out of storage). */
    data class DownloadFailed(override val message: String, val failedFiles: List<String> = emptyList()) : FlintError

    /** The game process started but exited abnormally (crash, JVM fault). */
    data class Crash(override val message: String, val exitCode: Int? = null, val logSnippet: String? = null) : FlintError

    /** Authentication problem: missing account, expired token, offline not allowed. */
    data class AuthError(override val message: String) : FlintError

    /** Version/profile metadata is missing, corrupt or internally inconsistent. */
    data class ConfigError(override val message: String) : FlintError

    /** The user (or a parent coroutine) cancelled the operation. Not shown as an error. */
    data object Cancelled : FlintError {
        override val message: String get() = "Cancelled"
    }

    /** Something we did not categorize. Always accompanied by a readable message. */
    data class Unknown(override val message: String, val causeType: String? = null) : FlintError
}
