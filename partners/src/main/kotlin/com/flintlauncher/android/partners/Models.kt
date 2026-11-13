package com.flintlauncher.android.partners

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Edition {
    @SerialName("java") JAVA,
    @SerialName("bedrock") BEDROCK,
}

@Serializable
data class ServerEntry(
    val name: String,
    val address: String,
    val edition: Edition = Edition.JAVA,
    val website: String? = null,
)

/** Result of a real status lookup. There is no "assumed online" value. */
sealed interface ServerStatusResult {
    data class Online(val players: Int?, val max: Int?) : ServerStatusResult
    data object Offline : ServerStatusResult
    data class Unknown(val reason: String) : ServerStatusResult
}

data class ServerWithStatus(val entry: ServerEntry, val status: ServerStatusResult)
