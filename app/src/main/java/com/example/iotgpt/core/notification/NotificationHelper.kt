package com.example.iotgpt.core.notification

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.iotgpt.MainActivity
import com.example.iotgpt.R

/**
 * Creates notification channels and posts system notifications for app events.
 */
class NotificationHelper(
    private val context: Context
) {
    fun ensureChannels() {
        val channel = NotificationChannel(
            CHANNEL_AI_REPLY,
            "AI 回复",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "AI 回复完成、任务完成等状态通知"
        }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun showAssistantReply(
        conversationId: String,
        title: String,
        content: String
    ) {
        ensureChannels()
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_AI_REPLY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title.ifBlank { "AI 回复完成" })
            .setContentText(content.take(MAX_NOTIFICATION_CHARS))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(content.take(MAX_BIG_TEXT_CHARS))
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        postNotification(conversationId.hashCode(), notification)
    }

    fun showTaskComplete(
        taskId: String,
        title: String,
        content: String
    ) {
        ensureChannels()
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_AI_REPLY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content.take(MAX_NOTIFICATION_CHARS))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.take(MAX_BIG_TEXT_CHARS)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        postNotification(taskId.hashCode(), notification)
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(id: Int, notification: Notification) {
        if (!hasNotificationPermission()) return
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        private const val CHANNEL_AI_REPLY = "ai_reply"
        private const val MAX_NOTIFICATION_CHARS = 80
        private const val MAX_BIG_TEXT_CHARS = 320
    }
}
