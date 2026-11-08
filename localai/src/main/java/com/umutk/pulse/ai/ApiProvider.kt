package com.umutk.pulse.ai

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import kotlinx.coroutines.runBlocking

/**
 * Text tools for Pulse (the keyboard's "AI fix"): contentResolver.call("content://com.umutk.pulse.extra.ai.api", "fix", text, null) returns
 * the text with its spelling, punctuation and capitals mended, in "text". Only Pulse (com.umutk.pulse) may call it; it runs on the caller's thread.
 */
class ApiProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val c = context ?: return null
        val caller = c.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if (caller.none { it == "com.umutk.pulse" || it == c.packageName }) return null
        val text = arg?.take(4000) ?: return null
        val sys = when (method) {
            "fix" -> "You correct text. Fix spelling, typos, punctuation, capital letters and Turkish characters (ı ş ğ ü ö ç) in the user's message. " +
                "Keep its language, words, meaning and tone. Reply with the corrected text only, no quotes, no notes."
            "formal" -> "Rewrite the user's message politely and formally in the same language. Reply with the text only."
            "translate_en" -> "Translate the user's message into English. Reply with the translation only."
            "translate_tr" -> "Translate the user's message into Turkish. Reply with the translation only."
            else -> return null
        }
        val out = runBlocking { runCatching { Engine.once(c, sys, text, (text.length / 2 + 64).coerceAtMost(1024)) }.getOrNull() } ?: return Bundle().apply { putString("error", "no model") }
        return Bundle().apply { putString("text", out) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
