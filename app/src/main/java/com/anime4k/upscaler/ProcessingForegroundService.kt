package com.anime4k.upscaler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

const val PROCESSING_CHANNEL_ID = "anime4k_processing"
const val PROCESSING_NOTIFICATION_ID = 4040

class ProcessingForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureProcessingChannel(this)
        val text = intent?.getStringExtra("text") ?: "Anime4K работает"
        startForeground(PROCESSING_NOTIFICATION_ID, buildProcessingNotification(this, text, -1))
        return START_NOT_STICKY
    }
}

fun ensureProcessingChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            PROCESSING_CHANNEL_ID,
            "Anime4K обработка",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Статус апскейла фото и видео"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}

fun buildProcessingNotification(context: Context, text: String, progressPercent: Int): Notification {
    val builder = NotificationCompat.Builder(context, PROCESSING_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("Anime4K Апскейлер")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
    if (progressPercent in 0..100) {
        builder.setProgress(100, progressPercent, false)
    } else {
        builder.setProgress(0, 0, true)
    }
    return builder.build()
}

fun startProcessingForeground(context: Context, text: String) {
    ensureProcessingChannel(context)
    val intent = Intent(context, ProcessingForegroundService::class.java).putExtra("text", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

fun updateProcessingNotification(context: Context, text: String, progress: Float) {
    ensureProcessingChannel(context)
    val manager = context.getSystemService(NotificationManager::class.java)
    val percent = (progress.coerceIn(0f, 1f) * 100f).toInt()
    manager.notify(PROCESSING_NOTIFICATION_ID, buildProcessingNotification(context, text, percent))
}

fun stopProcessingForeground(context: Context) {
    runCatching { context.stopService(Intent(context, ProcessingForegroundService::class.java)) }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.cancel(PROCESSING_NOTIFICATION_ID)
}
