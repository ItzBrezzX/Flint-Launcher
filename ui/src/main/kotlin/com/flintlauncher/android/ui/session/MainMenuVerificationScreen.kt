package com.flintlauncher.android.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF07110B)
private val Panel = Color(0xFF101B14)
private val MainText = Color(0xFFE8F0E8)
private val Muted = Color(0xFF8A9A8E)
private val Blocked = Color(0xFFFFC27A)

/**
 * Honest main-menu verification checklist. Every item that requires a real device
 * (rendering the title screen, touch/keyboard input, audio, world entry) is marked
 * BLOCKED. Nothing here is faked: this screen exists so a device tester can tick off
 * items as they are confirmed.
 */
@Composable
fun MainMenuVerificationScreen(onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Main menu verification", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Confirm each item on a real device. Items marked BLOCKED cannot be verified in the emulator/build and are not claimed done.",
            color = Muted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        VerificationRow(Verified, "JVM launch thread started", "Confirmed by LaunchResult.Started.")
        VerificationRow(Blocked, "Minecraft title screen renders", "Requires LWJGL/GLFW Android hand-off + device GPU.")
        VerificationRow(Blocked, "Touch input / on-screen controls", "Requires control overlay integration + device.")
        VerificationRow(Blocked, "Keyboard/mouse (USB/Bluetooth)", "Requires device input routing.")
        VerificationRow(Blocked, "Audio plays (OpenAL plugin)", "Requires audio backend + device.")
        VerificationRow(Blocked, "Enter single-player world", "Requires full gameplay loop + device.")
        VerificationRow(Blocked, "Join multiplayer server", "Requires networking + device.")
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Close") }
    }
}

private object Verified
private object Blocked

@Composable
private fun VerificationRow(kind: Any, title: String, detail: String) {
    val isBlocked = kind === Blocked
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val icon: ImageVector = if (isBlocked) Icons.Filled.Block else Icons.Filled.CheckCircle
        val tint: Color = if (isBlocked) Blocked else Color(0xFF4CAF50)
        Icon(icon, contentDescription = if (isBlocked) "Blocked" else "Verified", tint = tint, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MainText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
        Text(
            if (isBlocked) "BLOCKED" else "DONE",
            color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        )
    }
}
