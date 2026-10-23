package com.umutk.pulse.extras.messages

import android.app.Activity
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import com.umutk.pulse.SmsMsg
import com.umutk.pulse.SmsThread

/** Default-SMS-app state, the verification-code finder and the MMS reading shared by the receivers and the screen. */
object MsgCore {
    fun isDefault(c: Context) = runCatching { Telephony.Sms.getDefaultSmsPackage(c) == c.packageName }.getOrDefault(false)

    /** The intent that asks the user to make Pulse the default SMS app. */
    fun defaultRequest(c: Context): Intent =
        if (Build.VERSION.SDK_INT >= 29) c.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
        else Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, c.packageName)

    private val keyWords = Regex("(code|kod|otp|pin|password|passcode|şifre|sifre|doğrulama|dogrulama|verification|verify|onay|giriş|security|güvenlik|token)", RegexOption.IGNORE_CASE)
    private val dashed = Regex("""\b\d{3}[- ]\d{3}\b""")
    private val plain = Regex("""(?<![\d+])\d{4,8}(?![\d])""")
    private val alnum = Regex("""\b(?=[A-Z0-9]*\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{5,8}\b""")

    /** A verification code in [body] (4-8 digits, or 3+3 with a dash), only when the text talks about a code. */
    fun otp(body: String): String? {
        if (!keyWords.containsMatchIn(body)) return null
        dashed.find(body)?.let { return it.value.replace(" ", "").replace("-", "") }
        // the number right after or right before the key word is the code; fall back to the first plain number
        val near = Regex("""(?:${keyWords.pattern})\D{0,25}(\d{4,8})(?!\d)""", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)
        if (near != null) return near
        val before = Regex("""(?<![\d+])(\d{4,8})(?!\d)\D{0,25}(?:${keyWords.pattern})""", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)
        if (before != null) return before
        return plain.find(body)?.value ?: alnum.find(body)?.value
    }

    /** The phone numbers and web links in [body] as (start, end, kind) with kind "tel" or "url". */
    fun links(body: String): List<Triple<Int, Int, String>> {
        val out = mutableListOf<Triple<Int, Int, String>>()
        Regex("""https?://[^\s]+|www\.[^\s]+""", RegexOption.IGNORE_CASE).findAll(body).forEach { out += Triple(it.range.first, it.range.last + 1, "url") }
        Regex("""\+?\d[\d ()\-]{6,16}\d""").findAll(body).forEach { m ->
            val digits = m.value.count { it.isDigit() }
            if (digits in 7..15 && out.none { m.range.first < it.second && m.range.last + 1 > it.first }) out += Triple(m.range.first, m.range.last + 1, "tel")
        }
        return out.sortedBy { it.first }
    }

    fun activityOf(c: Context): Activity? {
        var x = c
        while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }
        return null
    }

    /** The address a started activity should open: the "msgAddress" extra, or an sms:/smsto:/mms:/mmsto: link. */
    fun addressOf(i: Intent?): String? {
        i ?: return null
        i.getStringExtra("msgAddress")?.let { return it }
        val d = i.data ?: return null
        if (d.scheme !in listOf("sms", "smsto", "mms", "mmsto")) return null
        return Uri.decode(d.schemeSpecificPart.substringBefore('?')).takeIf { it.isNotBlank() }
    }

    fun draftOf(i: Intent?): String? = i?.getStringExtra("sms_body") ?: i?.getStringExtra(Intent.EXTRA_TEXT)

    /** Stores an incoming message in the system provider (only the default SMS app may). */
    fun storeInbox(c: Context, address: String, body: String, date: Long, sent: Long) {
        val v = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.DATE_SENT, sent); put(Telephony.Sms.READ, 0); put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        runCatching { c.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, v) }
    }

    /** The default SMS app has to write its own sent messages; any other app's send is stored by the system. */
    fun storeSent(c: Context, address: String, body: String) {
        if (!isDefault(c)) return
        val v = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1); put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        runCatching { c.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, v) }
    }

    /** Marks every unread message of [address] read (the default app only); otherwise nothing changes in the provider. */
    fun markRead(c: Context, address: String) {
        if (!isDefault(c)) return
        val v = ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }
        runCatching { c.contentResolver.update(Telephony.Sms.CONTENT_URI, v, "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(address)) }
    }

    // ---- MMS ----

    private val mmsUri = Uri.parse("content://mms")

    /** Text and picture parts of one MMS. */
    fun mmsParts(c: Context, id: Long): Pair<String, List<Uri>> {
        val sb = StringBuilder()
        val pics = mutableListOf<Uri>()
        runCatching {
            c.contentResolver.query(Uri.parse("content://mms/part"), arrayOf("_id", "ct", "text"), "mid = ?", arrayOf(id.toString()), "_id ASC")?.use { cur ->
                while (cur.moveToNext()) {
                    val ct = cur.getString(1).orEmpty()
                    when {
                        ct == "text/plain" -> cur.getString(2)?.let { if (sb.isNotEmpty()) sb.append('\n'); sb.append(it) }
                        ct.startsWith("image/") -> pics += Uri.parse("content://mms/part/${cur.getLong(0)}")
                    }
                }
            }
        }
        return sb.toString() to pics
    }

    /** The other side of an MMS: the sender (type 137) of a received one, the first recipient (151) of a sent one. */
    fun mmsAddress(c: Context, id: Long, received: Boolean): String = runCatching {
        c.contentResolver.query(Uri.parse("content://mms/$id/addr"), arrayOf("address", "type"), null, null, null)?.use { cur ->
            var any = ""
            while (cur.moveToNext()) {
                val a = cur.getString(0).orEmpty()
                if (a.isEmpty() || a == "insert-address-token") continue
                val t = cur.getInt(1)
                if (received && t == 137) return@use a
                if (!received && t == 151) return@use a
                if (any.isEmpty()) any = a
            }
            any
        }.orEmpty()
    }.getOrDefault("")

    /** MMS rows of a thread (all threads when [tid] < 0 and [address] is blank are not asked for). */
    fun loadMms(c: Context, tid: Long): List<SmsMsg> {
        if (tid < 0) return emptyList()
        val out = mutableListOf<SmsMsg>()
        runCatching {
            c.contentResolver.query(mmsUri, arrayOf("_id", "date", "msg_box"), "thread_id = ?", arrayOf(tid.toString()), "date ASC")?.use { cur ->
                while (cur.moveToNext()) {
                    val (text, pics) = mmsParts(c, cur.getLong(0))
                    if (text.isEmpty() && pics.isEmpty()) continue
                    out += SmsMsg(text, cur.getInt(2) != 1, cur.getLong(1) * 1000, pics, true)
                }
            }
        }
        return out
    }

    /** The newest MMS of every thread, for the conversation list. */
    fun newestMms(c: Context): List<SmsThread> {
        val seen = HashSet<Long>()
        val out = mutableListOf<SmsThread>()
        runCatching {
            c.contentResolver.query(mmsUri, arrayOf("_id", "thread_id", "date", "msg_box", "read"), null, null, "date DESC")?.use { cur ->
                while (cur.moveToNext()) {
                    val tid = cur.getLong(1)
                    if (!seen.add(tid)) continue
                    val id = cur.getLong(0)
                    val (text, pics) = mmsParts(c, id)
                    val snip = text.ifEmpty { if (pics.isNotEmpty()) "🖼" else "MMS" }
                    out += SmsThread(tid, mmsAddress(c, id, cur.getInt(3) == 1), null, snip, cur.getLong(2) * 1000)
                }
            }
        }
        return out
    }

    /** SmsManager that follows the default subscription. */
    fun sms(c: Context): SmsManager = c.getSystemService(SmsManager::class.java)
}
