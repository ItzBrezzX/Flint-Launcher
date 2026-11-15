package com.flintlauncher.android.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.auth.DeviceCodePollStatus
import com.flintlauncher.android.ui.state.SignInActions
import com.flintlauncher.android.ui.state.SignInPhase
import com.flintlauncher.android.ui.state.SignInUiState

private val Bg = Color(0xFF07110B)
private val Panel = Color(0xFF101B14)
private val Accent = Color(0xFF35D12C)
private val MainText = Color(0xFFE8F0E8)
private val Muted = Color(0xFF8A9A8E)
private val Border = Color(0xFF1A2A1E)
private val Error = Color(0xFFFF8A80)

/**
 * Microsoft account sign-in. Two honest flows:
 *  - Browser PKCE (custom-scheme redirect, no client secret, no password handling).
 *  - Device code (sign in on another device, poll here) — fallback when redirect fails.
 * When this build has no Azure client id configured, both are disabled and a clear
 * "not configured" notice is shown instead of a fake button.
 */
@Composable
fun SignInScreen(state: SignInUiState, actions: SignInActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(Bg)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sign in with Microsoft", color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!state.isBusy && state.phase != SignInPhase.Succeeded) {
                TextButton(onClick = { actions.onDismiss?.invoke() }, enabled = actions.onDismiss != null) { Text("Close") }
            }
        }
        Text(
            "Flint never sees your password. You sign in on Microsoft's page and only a one-time code returns to the app.",
            color = Muted, fontSize = 13.sp,
        )

        if (!state.configured) {
            NoticePanel(
                "Microsoft sign-in is not configured in this build.\n\nAn Azure app registration client id must be baked in at build time. Offline accounts remain available.",
            )
            return@Column
        }

        when (state.phase) {
            SignInPhase.Choose -> ChoosePanel(actions)
            SignInPhase.PkceWaiting -> PkceWaitingPanel(actions)
            SignInPhase.DeviceCodeWaiting -> DeviceCodePanel(state, actions)
            SignInPhase.Succeeded -> SuccessPanel(state.successUsername, actions)
            SignInPhase.Failed -> FailedPanel(state.errorMessage, actions)
        }
    }
}

@Composable
private fun ChoosePanel(actions: SignInActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { actions.onStartPkce?.invoke() },
            enabled = actions.onStartPkce != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
        ) {
            Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
            Text("Sign in in browser (PKCE)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = { actions.onStartDeviceCode?.invoke() },
            enabled = actions.onStartDeviceCode != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Use a device code instead") }
        Text(
            "The device-code flow lets you sign in on a phone or computer and approve here. Useful if the browser redirect does not return to the app.",
            color = Muted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun PkceWaitingPanel(actions: SignInActions) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = Accent)
        Text("Complete sign-in in your browser…", color = MainText, fontWeight = FontWeight.Medium)
        Text("After approving, you will return to Flint automatically.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        TextButton(onClick = { actions.onCancel?.invoke() }, enabled = actions.onCancel != null) { Text("Cancel") }
    }
}

@Composable
private fun DeviceCodePanel(state: SignInUiState, actions: SignInActions) {
    val challenge = state.deviceChallenge
    if (challenge == null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
            Text("Requesting a device code…", color = Muted, fontSize = 13.sp)
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Step 1 — open this address on any device:", color = Muted, fontSize = 12.sp)
        Text(
            challenge.verificationUri,
            color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(enabled = actions.onOpenVerificationUrl != null) {
                actions.onOpenVerificationUrl?.invoke(challenge.verificationUri)
            },
        )
        Text("Step 2 — enter this code:", color = Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                challenge.userCodeDisplay,
                color = MainText, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { actions.onCopyUserCode?.invoke(challenge.userCode) }, enabled = actions.onCopyUserCode != null) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy device code", tint = Accent)
            }
        }
        when (val poll = state.pollStatus) {
            null, is DeviceCodePollStatus.Waiting, is DeviceCodePollStatus.SlowedDown -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                Text(
                    if (poll is DeviceCodePollStatus.SlowedDown) "Waiting for approval (slowed by server)…" else "Waiting for approval…",
                    color = Muted, fontSize = 12.sp,
                )
            }
            else -> Unit
        }
        TextButton(onClick = { actions.onCancel?.invoke() }, enabled = actions.onCancel != null) { Text("Cancel") }
    }
}

@Composable
private fun SuccessPanel(username: String?, actions: SignInActions) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp))
            .border(1.dp, Accent, RoundedCornerShape(12.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Signed in", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(username ?: "Your account", color = MainText, fontWeight = FontWeight.Medium)
        Text("The account is saved and selected. Your refresh token is stored encrypted; the access token stays in memory only.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Button(
            onClick = { actions.onDismiss?.invoke() },
            enabled = actions.onDismiss != null,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
        ) { Text("Done") }
    }
}

@Composable
private fun FailedPanel(message: String?, actions: SignInActions) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp))
            .border(1.dp, Error, RoundedCornerShape(12.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Sign-in failed", color = Error, fontWeight = FontWeight.Bold)
        Text(message ?: "Unknown error.", color = MainText, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { actions.onStartPkce?.invoke() }, enabled = actions.onStartPkce != null) { Text("Try browser again") }
            TextButton(onClick = { actions.onStartDeviceCode?.invoke() }, enabled = actions.onStartDeviceCode != null) { Text("Try device code") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { actions.onDismiss?.invoke() }, enabled = actions.onDismiss != null) { Text("Close") }
        }
    }
}

@Composable
private fun NoticePanel(text: String) {
    Box(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(16.dp),
    ) {
        Text(text, color = Muted, fontSize = 13.sp)
    }
}
