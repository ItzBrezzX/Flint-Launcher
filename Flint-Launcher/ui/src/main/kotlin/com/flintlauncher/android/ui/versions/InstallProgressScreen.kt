package com.flintlauncher.android.ui.versions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flintlauncher.android.installer.InstallStage
import com.flintlauncher.android.ui.state.InstallPanelState
import com.flintlauncher.android.ui.state.InstallPhase
import com.flintlauncher.android.ui.state.VersionsActions
import com.flintlauncher.android.ui.theme.FlintColors

/**
 * Dedicated real-time Minecraft version installation UI.
 *
 * All progress numbers (bytes, speed, ETA, file counts) come from [InstallPanelState]
 * which is fed by the real [com.flintlauncher.android.download.DownloadManager].
 * No simulated timers or hardcoded sizes.
 */
@Composable
fun InstallProgressScreen(
    panel: InstallPanelState,
    actions: VersionsActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FlintColors.Surface)
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Text(
            text = "MINECRAFT ${panel.versionId.uppercase()}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = FlintColors.TextPrimary,
        )
        Text(
            text = panel.versionTypeLabel,
            style = MaterialTheme.typography.labelLarge,
            color = FlintColors.TextMuted,
        )

        when (panel.phase) {
            InstallPhase.RUNNING -> RunningPanel(panel, actions)
            InstallPhase.SUCCEEDED -> CompletionPanel(panel, actions)
            InstallPhase.FAILED, InstallPhase.CANCELLED -> ErrorPanel(panel, actions)
        }
    }
}

@Composable
private fun RunningPanel(panel: InstallPanelState, actions: VersionsActions) {
    val targetFraction = panel.percent?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) } ?: 0f
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "progress",
    )

    // Stage label with cross-fade
    AnimatedContent(
        targetState = panel.stageLabel,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically { it / 4 }) togetherWith
                (fadeOut(tween(180)) + slideOutVertically { -it / 4 })
        },
        label = "stage",
    ) { stage ->
        Text(
            text = stage,
            style = MaterialTheme.typography.titleMedium,
            color = FlintColors.Ember,
            fontWeight = FontWeight.SemiBold,
        )
    }

    // Large percentage
    val pctText = Formatters.percent(panel.percent)
    Text(
        text = pctText,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = FlintColors.TextPrimary,
    )

    // Animated progress bar
    LinearProgressIndicator(
        progress = { if (panel.totalBytes == null) 0f else animatedFraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp)),
        color = FlintColors.Ember,
        trackColor = FlintColors.SurfaceVariant,
    )

    // Stats grid
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatRow("Downloaded", "${Formatters.bytes(panel.downloadedBytes)} / ${panel.totalBytes?.let { Formatters.bytes(it) } ?: "unknown"}")
        if (panel.paused) {
            StatRow("Status", "Paused")
        } else {
            StatRow("Speed", Formatters.speed(panel.speedBytesPerSecond))
            StatRow("Average", Formatters.speed(panel.averageBytesPerSecond))
            StatRow("Remaining", Formatters.duration(panel.etaSeconds))
        }
        StatRow("Files", "${panel.completedFiles} / ${panel.totalFiles}" + if (panel.failedFiles > 0) "  ·  ${panel.failedFiles} failed" else "")
        if (panel.unknownSizeFiles > 0) {
            StatRow("Unknown size", "${panel.unknownSizeFiles} file(s)")
        }
    }

    // Current file
    AnimatedVisibility(visible = panel.activeFile != null) {
        Column(Modifier = Modifier.padding(top = 8.dp)) {
            Text("Current file", style = MaterialTheme.typography.labelMedium, color = FlintColors.TextMuted)
            Text(
                text = panel.activeFile ?: "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = FlintColors.TextPrimary,
            )
        }
    }

    // Pulse indicator while running and not paused
    if (!panel.paused) {
        val infinite = rememberInfiniteTransition(label = "pulse")
        val alpha by infinite.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "alpha",
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { this.alpha = alpha },
                strokeWidth = 2.dp,
                color = FlintColors.Ember,
            )
            Spacer(Modifier = Modifier.width(8.dp))
            Text(
                text = if (panel.speedBytesPerSecond <= 0) "Waiting for network…" else "Downloading",
                style = MaterialTheme.typography.labelMedium,
                color = FlintColors.TextMuted,
            )
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    // Controls
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (panel.paused) {
            Button(
                onClick = { actions.onResume?.invoke() },
                enabled = actions.onResume != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = FlintColors.Ember, contentColor = FlintColors.OnEmber),
            ) { Text("Resume") }
        } else {
            OutlinedButton(
                onClick = { actions.onPause?.invoke() },
                enabled = actions.onPause != null,
                modifier = Modifier.weight(1f),
            ) { Text("Pause") }
        }
        OutlinedButton(
            onClick = { actions.onCancel?.invoke() },
            enabled = actions.onCancel != null,
            modifier = Modifier.weight(1f),
        ) { Text("Cancel") }
    }
}

@Composable
private fun CompletionPanel(panel: InstallPanelState, actions: VersionsActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "FLINT INSTALLATION COMPLETE",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = FlintColors.StatusOnline,
        )
        Text(
            text = "Minecraft ${panel.versionId}",
            style = MaterialTheme.typography.headlineSmall,
            color = FlintColors.TextPrimary,
        )
        Text(
            text = "Installation verified.",
            style = MaterialTheme.typography.bodyLarge,
            color = FlintColors.TextMuted,
        )
        Text(
            text = "Status: READY TO PLAY",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = FlintColors.StatusOnline,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { actions.onPlayNow?.invoke(panel.versionId) },
            enabled = actions.onPlayNow != null && panel.readyToPlay,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = FlintColors.Ember, contentColor = FlintColors.OnEmber),
        ) { Text("PLAY NOW") }

        OutlinedButton(
            onClick = { actions.onViewInstallation?.invoke(panel.versionId) },
            enabled = actions.onViewInstallation != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("VIEW INSTALLATION") }

        TextButton(
            onClick = { actions.onManageMods?.invoke(panel.versionId) },
            enabled = actions.onManageMods != null,
        ) { Text("MANAGE MODS") }

        TextButton(onClick = { actions.onDismissResult?.invoke() }) { Text("Done") }
    }
}

@Composable
private fun ErrorPanel(panel: InstallPanelState, actions: VersionsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (panel.phase == InstallPhase.CANCELLED) "Installation cancelled" else "Installation failed",
            style = MaterialTheme.typography.titleLarge,
            color = FlintColors.StatusOffline,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = panel.message ?: "Unknown error",
            style = MaterialTheme.typography.bodyMedium,
            color = FlintColors.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (panel.phase == InstallPhase.FAILED) {
                Button(
                    onClick = { actions.onInstall?.invoke(panel.versionId) },
                    enabled = actions.onInstall != null,
                    colors = ButtonDefaults.buttonColors(containerColor = FlintColors.Ember, contentColor = FlintColors.OnEmber),
                ) { Text("Retry") }
                if (actions.onRepair != null) {
                    OutlinedButton(onClick = { actions.onRepair?.invoke(panel.versionId) }) {
                        Text("Repair")
                    }
                }
            }
            TextButton(onClick = { actions.onDismissResult?.invoke() }) { Text("Dismiss") }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = FlintColors.TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = FlintColors.TextPrimary)
    }
}
