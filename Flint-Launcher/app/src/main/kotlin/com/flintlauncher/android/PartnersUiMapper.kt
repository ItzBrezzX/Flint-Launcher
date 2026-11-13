package com.flintlauncher.android

import com.flintlauncher.android.partners.Edition
import com.flintlauncher.android.partners.ServerStatusResult
import com.flintlauncher.android.partners.ServerWithStatus
import com.flintlauncher.android.ui.state.PartnerServerUi
import com.flintlauncher.android.ui.state.ServerEdition
import com.flintlauncher.android.ui.state.ServerStatus

internal fun ServerWithStatus.toUi(): PartnerServerUi = PartnerServerUi(
    name = entry.name,
    address = entry.address,
    edition = if (entry.edition == Edition.BEDROCK) ServerEdition.BEDROCK else ServerEdition.JAVA,
    status = when (val result = status) {
        is ServerStatusResult.Online -> ServerStatus.Online(result.players)
        ServerStatusResult.Offline -> ServerStatus.Offline
        is ServerStatusResult.Unknown -> ServerStatus.Unknown
    },
    website = entry.website,
)
