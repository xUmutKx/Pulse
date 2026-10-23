package com.umutk.pulse.extras.keyboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat

/**
 * The words of the person typing, from what is already on the phone: the messages sent from it (the words and which word follows which), the names in the
 * contacts and the phone's own user dictionary. Each source needs its permission; one that is not granted is skipped and reported.
 */
object WordMiner {
    class Report(val sms: Int, val names: Int, val system: Int, val skipped: List<String>)

    fun needed(c: Context) = listOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS).filter { ContextCompat.checkSelfPermission(c, it) != PackageManager.PERMISSION_GRANTED }

    /** Blocking: call off the main thread. */
    fun run(c: Context): Report {
        val skipped = ArrayList<String>()
        var sms = 0; var names = 0; var sys = 0
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) runCatching {
            // the messages the person wrote, newest first, up to 3000
            c.contentResolver.query(Telephony.Sms.Sent.CONTENT_URI, arrayOf(Telephony.Sms.BODY), null, null, "${Telephony.Sms.DATE} DESC LIMIT 3000")?.use { cur ->
                while (cur.moveToNext()) { val b = cur.getString(0).orEmpty(); if (b.isNotBlank()) { KeyboardDictionary.learnText(c, b); sms++ } }
            }
        } else skipped += "SMS"
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) runCatching {
            c.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cur ->
                while (cur.moveToNext()) cur.getString(0)?.let { n -> n.split(' ', '-').filter { it.length >= 2 && it.all { ch -> ch.isLetter() } }.forEach { w -> KeyboardDictionary.learnWord(w, 3) ; names++ } }
            }
        } else skipped += tr2("contacts", "kişiler")
        // the user dictionary of the phone (words added by hand in the system settings); the system may refuse it
        runCatching {
            @Suppress("DEPRECATION")
            c.contentResolver.query(android.provider.UserDictionary.Words.CONTENT_URI, arrayOf(android.provider.UserDictionary.Words.WORD), null, null, null)?.use { cur ->
                while (cur.moveToNext()) cur.getString(0)?.takeIf { it.length >= 2 }?.let { KeyboardDictionary.learnWord(it, 4); sys++ }
            }
        }
        KeyboardDictionary.saveUser(c, force = true)
        return Report(sms, names, sys, skipped)
    }

    private fun tr2(en: String, tr: String) = com.umutk.pulse.tr(en, tr)
}
