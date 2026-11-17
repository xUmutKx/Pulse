package com.drdisagree.glasslayer.xposed.prefs

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.CopyOnWriteArrayList

object ModulePrefs {

    private var prefs: SharedPreferences? = null
    private val listeners = CopyOnWriteArrayList<(String?) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        mainHandler.post { listeners.forEach { listener -> Guard.run(TAG, Unit) { listener(key) } } }
    }

    fun init(module: XposedInterface) {
        prefs = try {
            module.getRemotePreferences(PrefKeys.GROUP)
        } catch (throwable: Throwable) {
            HookEnv.log("Remote preferences unavailable", throwable)
            null
        }
        prefs?.registerOnSharedPreferenceChangeListener(changeListener)
    }

    fun addListener(listener: (String?) -> Unit) {
        listeners += listener
    }

    fun boolean(key: String, default: Boolean): Boolean = try {
        prefs?.getBoolean(key, default) ?: default
    } catch (_: Throwable) {
        default
    }

    fun long(key: String, default: Long): Long = try {
        prefs?.getLong(key, default) ?: default
    } catch (_: Throwable) {
        default
    }

    fun float(key: String, default: Float): Float = try {
        prefs?.getFloat(key, default) ?: default
    } catch (_: Throwable) {
        default
    }
}

private const val TAG = "ModulePrefs.listener"
