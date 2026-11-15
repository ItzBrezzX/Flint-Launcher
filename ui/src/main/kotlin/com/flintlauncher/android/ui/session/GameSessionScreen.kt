package com.flintlauncher.android.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.ui.state.GameSessionActions
import com.flintlauncher.android.ui.state.GameSessionPhase
import com.flintlauncher.android.ui.state.GameSessionUiState

private val Bg = Color(0xFF07110B)
private val Panel = Color(0xFF101B14)
private val Accent = Color(0xFF35D12C)
private val MainText = Color(0xFFE8F0E8)
private val Muted = Color(0xFF8A9A8E)
private val Border = Color(0xFF1A2A1E)
private val Error = Color(0xFFFF8A80)

/**
 * Full-screen game session. Hosts the real Android Surface (via AndroidView binding
 * GL4ESSurfaceView) once the JVM is running. While preparing/loading it shows an
 * honest progress screen. On crash/exit it shows [CrashScreen] with recovery.
 *
 * BLOCKED (device only): actual Minecraft main menu rendering, world entry, and
 * gameplay require the LWJGL/GLFW Android hand-off which cannot be verified here.
 * The SurfaceView shows the GL4ES clear-color test frame, not Minecraft.
 */
@Composable
fun GameSessionScreen(
    state: GameSessionUiState,
    actions: GameSessionActions,
    surfaceFactory: () -> android.view.View,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.BLACK)) {
        when (state.phase) {
            GameSessionPhase.Preparing, GameSessionPhase.Loading -> LoadingScreen(state, actions)
            GameSessionPhase.Running -> {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { surfaceFactory() },
                    modifier = Modifier.fillMaxSize(),
                )
                RunningOverlay(state, actions)
            }
            GameSessionPhase.Crashed, GameSessionPhase.Exited -> CrashScreen(state, actions)
        }
    }
}

@Composable
private fun LoadingScreen(state: GameSessionUiState, actions: GameSessionActions) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            "Minecraft ${state.versionId}",
            color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            state.statusMessage.ifBlank { "Preparing launch…" },
            color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            color = Accent, trackColor = Panel,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { actions.onStop?.invoke() }, enabled = actions.onStop != null) {
            Text("Cancel launch")
        }
    }
}

@Composable
private fun RunningOverlay(state: GameSessionUiState, actions: GameSessionActions) {
    var showVerification by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xB3000000))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.rendererStatus.ifBlank { "Renderer initializing…" },
                color = MainText, fontSize = 11.sp, modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            IconButton(onClick = { actions.onToggleFullscreen?.invoke() }, enabled = actions.onToggleFullscreen != null) {
                Icon(Icons.Filled.Refresh, contentDescription = "Toggle fullscreen", tint = MainText)
            }
            IconButton(onClick = { showVerification = true }, enabled = actions.onOpenVerification != null) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Main menu verification", tint = MainText)
            }
            IconButton(onClick = { actions.onStop?.invoke() }, enabled = actions.onStop != null) {
                Icon(Icons.Filled.Close, contentDescription = "Stop game", tint = Error)
            }
        }
        Spacer(Modifier.weight(1f))
        // Honest banner: running != playable. Never claim the main menu is reached.
        Text(
            "JVM running · Minecraft main menu / gameplay verification is BLOCKED on device test",
            color = Color(0xFFFFC27A), fontSize = 10.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xB3000000))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
    if (showVerification) {
        MainMenuVerificationScreen(onClose = { showVerification = false })
    }
}

@Composable
internal fun CrashScreen(state: GameSessionUiState, actions: GameSessionActions) {
    var showLog by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Bg).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (state.phase == GameSessionPhase.Crashed) "Minecraft crashed" else "Minecraft exited",
            color = Error, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        )
        state.failureReason?.let {
            Text(it, color = MainText, fontSize = 13.sp)
        }
        state.exitCode?.let {
            Text("Exit code: $it", color = Muted, fontSize = 12.sp)
        }
        val logScroll = rememberScrollState()
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Panel)
                .border(1.dp, Border, RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            Text(
                if (showLog) state.logTail.ifBlank { "(no log output captured)" } else "Log output is available. Tap \"View log\" to expand.",
                color = Muted, fontSize = 11.sp,
                modifier = if (showLog) Modifier.verticalScroll(logScroll) else Modifier,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Hide log" else "View log") }
            OutlinedButton(onClick = { actions.onCopyLog?.invoke() }, enabled = actions.onCopyLog != null) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.width(16.dp))
                Text("Copy", modifier = Modifier.padding(start = 4.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { actions.onRetry?.invoke() },
                enabled = actions.onRetry != null,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                modifier = Modifier.weight(1f),
            ) { Text("Retry", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { actions.onReturnToLauncher?.invoke() },
                enabled = actions.onReturnToLauncher != null,
                modifier = Modifier.weight(1f),
            ) { Text("Back to launcher") }
        }
    }
}
