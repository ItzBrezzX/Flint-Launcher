package com.flintlauncher.android

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.flintlauncher.android.nativebridge.FlintGameSurfaceView

@Composable
fun GameWindowScreen() {
    val context = LocalContext.current
    val status = remember { mutableStateOf("Waiting for Android surface…") }
    val surfaceView = remember {
        FlintGameSurfaceView(context).apply {
            onStatus = { status.value = it }
        }
    }

    DisposableEffect(surfaceView) {
        onDispose {
            surfaceView.onStatus = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07110B))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Game Window Bridge", color = Color(0xFFE8F0E8))
        Text(
            "Android SurfaceView → ANativeWindow → EGL/GLES2. This diagnostic surface is not yet connected to GLFW, LWJGL or Minecraft.",
            color = Color(0xFF9BA99E),
        )
        AndroidView(
            factory = { surfaceView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .background(Color.Black, RoundedCornerShape(8.dp)),
            update = { it.onStatus = { value -> status.value = value } },
        )
        Text("Bridge status: ${status.value}", color = Color(0xFF35D12C))
    }
}
