package com.drdisagree.glasslayer.xposed.oneui

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.drdisagree.glasslayer.data.source.OneUiKeys
import com.drdisagree.glasslayer.data.source.ReportContract
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.HostApplication
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import com.drdisagree.glasslayer.xposed.prefs.ModulePrefs
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * The One UI inspector: Samsung's System UI and apps use their own classes (not the AOSP / Pixel ones the glass hooks look for), and they can only be
 * found on the phone. With the inspector on, every window that is added (the shade, quick settings, the volume panel, the lock screen) and every screen
 * an app shows has its views written down a moment after it appears: the class, the id, the background drawable and the size, as a tree. The text goes to
 * the Glass Layer app, which saves it for the person to share; hooks for those exact classes are written from it.
 */
object Inspector {

    private const val TAG = "Inspector"
    private const val MAX_LINES = 700
    private const val MAX_DEPTH = 18
    private const val DELAY_MS = 1500L

    private val main = Handler(Looper.getMainLooper())
    private val seen = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    fun install(pkg: String): Int {
        if (!ModulePrefs.boolean(OneUiKeys.INSPECTOR, false)) return 0
        var hooks = 0
        // every window: System UI adds its panels as windows of their own
        hooks += HookEnv.findClass("android.view.WindowManagerGlobal").hookAllAfter("addView") { chain, _ ->
            val view = chain.args.firstOrNull() as? View ?: return@hookAllAfter
            val title = (chain.args.getOrNull(1) as? WindowManager.LayoutParams)?.title?.toString().orEmpty()
            later(pkg, "window $title", view)
        }
        // every screen of an app: its window's root, once it is shown
        hooks += HookEnv.findClass("android.app.Activity").hookAllAfter("onResume") { chain, _ ->
            val activity = chain.thisObject as? Activity ?: return@hookAllAfter
            val root = activity.window?.decorView ?: return@hookAllAfter
            later(pkg, "activity ${activity.javaClass.name}", root)
        }
        return hooks
    }

    private fun later(pkg: String, what: String, view: View) {
        val ref = WeakReference(view)
        main.postDelayed({ Guard.run(TAG, Unit) { ref.get()?.let { dump(pkg, what, it) } } }, DELAY_MS)
    }

    private fun dump(pkg: String, what: String, root: View) {
        if (!ModulePrefs.boolean(OneUiKeys.INSPECTOR, false)) return
        // a window that is shown again is written once per run
        if (!seen.add(root)) return
        val out = StringBuilder()
        out.append("== ").append(pkg).append(" · ").append(what).append(" · ").append(root.width).append('x').append(root.height).append('\n')
        var lines = 0
        fun walk(v: View, depth: Int) {
            if (lines >= MAX_LINES || depth > MAX_DEPTH) return
            if (v.visibility == View.GONE) return
            lines++
            out.append("  ".repeat(depth)).append(v.javaClass.name)
            val id = if (v.id != View.NO_ID) runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull() else null
            if (id != null) out.append(" #").append(id)
            v.background?.let { bg ->
                out.append(" bg=").append(bg.javaClass.simpleName)
                if (bg is ColorDrawable) out.append("(#").append(Integer.toHexString(bg.color)).append(')')
            }
            if (v.visibility == View.INVISIBLE) out.append(" invisible")
            out.append(' ').append(v.width).append('x').append(v.height).append('\n')
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
        }
        walk(root, 0)
        if (lines >= MAX_LINES) out.append("  … more views\n")
        send(pkg, out.toString())
    }

    private fun send(pkg: String, text: String) {
        val context = HostApplication.get() ?: return
        val intent = Intent(OneUiKeys.ACTION_INSPECT)
            .setPackage(ReportContract.APP_PACKAGE)
            .putExtra(OneUiKeys.EXTRA_SOURCE, pkg)
            // a broadcast carries a few hundred kilobytes at most: a very big tree is cut
            .putExtra(OneUiKeys.EXTRA_TEXT, if (text.length > 120_000) text.take(120_000) + "\n… cut\n" else text)
        context.sendBroadcast(intent)
    }
}
