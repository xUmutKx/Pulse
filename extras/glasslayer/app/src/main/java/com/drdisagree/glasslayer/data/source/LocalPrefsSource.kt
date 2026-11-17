package com.drdisagree.glasslayer.data.source

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class LocalPrefsSource(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PrefKeys.GROUP, Context.MODE_PRIVATE)

    fun boolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    fun float(key: String, default: Float): Float = prefs.getFloat(key, default)

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    fun putFloat(key: String, value: Float) {
        prefs.edit { putFloat(key, value) }
    }

    fun changes(filter: (String) -> Boolean): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || filter(key)) trySend(Unit)
        }
        trySend(Unit)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()
}
