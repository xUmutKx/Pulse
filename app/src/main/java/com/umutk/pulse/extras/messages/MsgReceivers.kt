package com.umutk.pulse.extras.messages

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Telephony
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.klinker.android.send_message.MmsReceivedReceiver
import com.umutk.pulse.ExtraToolActivity
import com.umutk.pulse.MsgPrefs
import com.umutk.pulse.sendSms
import com.umutk.pulse.tr

/** The automatic spam block: an unwanted message makes no notification and its number goes on the blocked list (Settings shows it, Unblock takes it off). */
internal fun spam(c: Context, addr: String, body: String): Boolean {
    if (!MsgPrefs.spamBlock(c)) return false
    if (!MsgCore.isSpam(com.umutk.pulse.contactName(c, addr) != null, body)) return false
    MsgPrefs.setBlocked(c, MsgPrefs.blocked(c) + addr)
    return true
}

/** SMS_DELIVER (Pulse is the default app: store the message, then notify). */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(i) }.getOrNull().orEmpty()
        if (parts.isEmpty()) return
        val addr = parts[0].displayOriginatingAddress ?: parts[0].originatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody ?: "" }
        if (addr in MsgPrefs.blocked(c)) return
        MsgCore.storeInbox(c, addr, body, System.currentTimeMillis(), parts[0].timestampMillis)
        if (spam(c, addr, body)) return
        MsgNotifier.incoming(c, addr, body)
    }
}

/** SMS_RECEIVED (another app is the default app: the system stores it, Pulse only notifies and copies the code). */
class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (MsgCore.isDefault(c)) return
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(i) }.getOrNull().orEmpty()
        if (parts.isEmpty()) return
        val addr = parts[0].displayOriginatingAddress ?: parts[0].originatingAddress ?: return
        if (addr in MsgPrefs.blocked(c)) return
        val body = parts.joinToString("") { it.displayMessageBody ?: "" }
        if (spam(c, addr, body)) return
        MsgNotifier.incoming(c, addr, body)
    }
}

/** A downloaded MMS (the library's push receiver starts the download, the system stores the message). */
class MmsInReceiver : MmsReceivedReceiver() {
    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val id = runCatching { messageUri.lastPathSegment?.toLong() }.getOrNull() ?: return
        val addr = MsgCore.mmsAddress(context, id, true).ifEmpty { return }
        if (addr in MsgPrefs.blocked(context)) return
        val (text, pics) = MsgCore.mmsParts(context, id)
        MsgNotifier.incoming(context, addr, text.ifEmpty { if (pics.isNotEmpty()) "🖼" else "MMS" })
    }
    override fun onError(context: Context, error: String) {}
}

/** Reply, mark read and copy-code buttons of the notification. */
class MsgActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val addr = i.getStringExtra("address").orEmpty()
        when (i.action) {
            MsgNotifier.A_COPY -> {
                i.getStringExtra("body")?.let { MsgNotifier.copy(c, it) }
                Toast.makeText(c, tr("Copied", "Kopyalandı"), Toast.LENGTH_SHORT).show()
            }
            MsgNotifier.A_READ -> { MsgCore.markRead(c, addr); MsgNotifier.cancel(c, addr) }
            MsgNotifier.A_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(i)?.getCharSequence(MsgNotifier.KEY_REPLY)?.toString()?.trim().orEmpty()
                if (text.isEmpty() || addr.isEmpty()) return
                if (sendSms(c, addr, text)) { MsgCore.markRead(c, addr); MsgNotifier.repliedTo(c, addr, text) }
                else Toast.makeText(c, tr("Not sent", "Gönderilemedi"), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** "Respond via message" of the phone app (a declined call answered with a text). Required to be a default SMS app. */
class MsgRespondService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val to = MsgCore.addressOf(intent)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!to.isNullOrBlank() && !text.isNullOrBlank()) sendSms(this, to, text)
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

/** The "new message" and share targets of other apps; also what the system asks for when choosing the default SMS app. */
class MsgComposeActivity : ExtraToolActivity()

/** The conversation shown as a chat bubble above other apps. */
class MsgBubbleActivity : ExtraToolActivity()
