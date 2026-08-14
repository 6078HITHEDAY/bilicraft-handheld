package com.bilicraft.handheld.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.bilicraft.handheld.AppContainer
import com.bilicraft.handheld.chat.ConversationIds
import com.bilicraft.handheld.chat.ConversationKind
import com.bilicraft.handheld.chat.StoredMessage
import com.bilicraft.handheld.ui.MainActivity
import java.io.File

class ChatNotificationManager(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun notifyMessage(
        serverId: String,
        conversationId: String,
        title: String,
        kind: ConversationKind,
        message: StoredMessage,
        iconFile: File?,
        notifyWhispers: Boolean,
        notifyMentions: Boolean,
        selfName: String?
    ) {
        val mention = !selfName.isNullOrBlank() && message.bodyPlain.contains(selfName, ignoreCase = true)
        val shouldAlert = when (kind) {
            ConversationKind.Whisper -> notifyWhispers
            ConversationKind.Public, ConversationKind.Channel -> notifyMentions && mention
            ConversationKind.System -> false
        }
        if (!shouldAlert) return
        ensureChannel()
        val self = Person.Builder().setName(selfName ?: "我").build()
        val person = Person.Builder().setName(message.sender ?: title).build()
        val shortcutId = "$serverId/$conversationId"
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SERVER_ID, serverId)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(
                context,
                ShortcutInfoCompat.Builder(context, shortcutId)
                    .setShortLabel(title.take(20).ifBlank { "聊天" })
                    .setLongLived(true)
                    .setIntent(open)
                    .setPerson(person)
                    .build()
            )
        }
        val style = NotificationCompat.MessagingStyle(self)
            .setConversationTitle(title)
            .addMessage(message.bodyPlain, message.timestamp, person)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(message.bodyPlain)
            .setStyle(style)
            .setAutoCancel(true)
            .setShortcutId(shortcutId)
            .setContentIntent(openIntent(serverId, conversationId))
            .addAction(replyAction(serverId, conversationId))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        iconFile?.takeIf { it.exists() }?.let { file ->
            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) builder.setLargeIcon(bmp)
        }
        manager.notify(notificationId(serverId, conversationId), builder.build())
    }

    fun cancel(serverId: String, conversationId: String) {
        manager.cancel(notificationId(serverId, conversationId))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "聊天消息", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
    }

    private fun openIntent(serverId: String, conversationId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SERVER_ID, serverId)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        return PendingIntent.getActivity(
            context,
            notificationId(serverId, conversationId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun replyAction(serverId: String, conversationId: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel("回复").build()
        val intent = Intent(context, ChatReplyReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_SERVER_ID, serverId)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId(serverId, conversationId) + 17,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "回复", pending)
            .addRemoteInput(remoteInput)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "chat-messages"
        const val EXTRA_SERVER_ID = "chat_server_id"
        const val EXTRA_CONVERSATION_ID = "chat_conversation_id"
        const val KEY_REPLY = "chat_reply"
        const val ACTION_REPLY = "com.bilicraft.handheld.CHAT_REPLY"

        fun notificationId(serverId: String, conversationId: String): Int =
            2000 + kotlin.math.abs((serverId + "/" + conversationId).hashCode() % 100000)
    }
}

class ChatReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ChatNotificationManager.KEY_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (reply.isBlank()) return
        val serverId = intent.getStringExtra(ChatNotificationManager.EXTRA_SERVER_ID) ?: return
        val conversationId = intent.getStringExtra(ChatNotificationManager.EXTRA_CONVERSATION_ID) ?: ConversationIds.PUBLIC
        AppContainer.init(context)
        AppContainer.chatDispatcher.replyFromNotification(serverId, conversationId, reply)
    }
}
