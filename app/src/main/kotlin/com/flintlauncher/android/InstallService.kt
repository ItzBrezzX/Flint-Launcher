package com.flintlauncher.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.flintlauncher.android.ui.versions.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and shows a progress notification while an install runs. The work
 * itself lives in [InstallCoordinator]; this service only observes it and stops when it ends.
 */
class InstallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val coordinator = (application as FlintApplication).container.installCoordinator
        createChannel()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(coordinator.state.value), type)

        observer?.cancel()
        observer = scope.launch {
            coordinator.state.collect { state ->
                if (state.phase == InstallState.Phase.RUNNING) {
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
                } else {
                    ServiceCompat.stopForeground(this@InstallService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    notificationManager().notify(RESULT_NOTIFICATION_ID, buildResultNotification(state))
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY // an interrupted install is resumed by the user from the app
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.install_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(state: InstallState): Notification {
        val p = state.progress
        val percent = p.percent
        val text = buildString {
            append(Formatters.bytes(p.downloadedBytes))
            p.totalBytes?.let { append(" / ").append(Formatters.bytes(it)) }
            if (p.speedBytesPerSecond > 0) append("  \u00B7  ").append(Formatters.speed(p.speedBytesPerSecond))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.install_title, state.versionId.orEmpty()))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .setProgress(100, percent?.toInt() ?: 0, percent == null)
            .build()
    }

    private fun buildResultNotification(state: InstallState): Notification {
        val ok = state.phase == InstallState.Phase.SUCCEEDED
        val id = state.versionId.orEmpty()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(getString(if (ok) R.string.install_done else R.string.install_stopped, id))
            .setContentText(state.message.orEmpty())
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "flint_install"
        const val NOTIFICATION_ID = 1001
        const val RESULT_NOTIFICATION_ID = 1002
    }
}
