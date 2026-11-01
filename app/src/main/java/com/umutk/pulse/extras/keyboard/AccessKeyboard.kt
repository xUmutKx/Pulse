package com.umutk.pulse.extras.keyboard

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.Locale

/** The settings of the accessibility keyboard: its own switch, separate from the input method's floating mode. */
object AccessKeyboard {
    private const val PREFS = "pulse_access_kb"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun on(c: Context) = prefs(c).getBoolean("on", false)
    fun setOn(c: Context, v: Boolean) = prefs(c).edit().putBoolean("on", v).apply()

    /** Whether Pulse's accessibility keyboard is switched on in the phone's accessibility settings. */
    fun serviceEnabled(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(c, AccessKeyboardService::class.java)
        return s.split(':').any { it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true) }
    }
}

/** Keeps the Compose keyboard alive in a window of its own (no activity behind it). */
private class AccessOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val reg = LifecycleRegistry(this)
    private val ctl = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ctl.savedStateRegistry
    fun start() { ctl.performRestore(Bundle()); reg.currentState = Lifecycle.State.RESUMED }
    fun stop() { reg.currentState = Lifecycle.State.DESTROYED }
}

/**
 * The floating keyboard of the accessibility service: when a text field is focused in any app, a keyboard window
 * is drawn over the screen (anywhere, dragged by its handle) and the keys write into the focused field.
 * It needs no overlay permission: the accessibility service draws it. Switched on by [AccessKeyboard].
 */
class AccessKeyboardService : AccessibilityService() {
    private var wm: WindowManager? = null
    private var view: ComposeView? = null
    private var owner: AccessOwner? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!AccessKeyboard.on(this)) { hideKeys(); return }
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val f = focusedField()
                if (f != null) { runCatching { f.recycle() }; showKeys() } else hideKeys()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> hideKeys()
        }
    }

    override fun onInterrupt() = hideKeys()

    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; hideKeys(); return super.onUnbind(intent) }

    override fun onDestroy() { instance = null; hideKeys(); super.onDestroy() }

    private fun showKeys() {
        if (view != null) return
        val w = wm ?: return
        val d = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            (d.widthPixels * 0.94f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // never takes the focus (the field keeps it), and touches outside the keyboard go to the app below
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (d.widthPixels * 0.03f).toInt()
            y = (d.heightPixels * 0.55f).toInt()
        }
        val o = AccessOwner().also { it.start() }
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                AccessKeys(
                    onKey = { commitText(it) },
                    onBackspace = { deleteBefore() },
                    onEnter = { enter() },
                    onDrag = { dx, dy -> params.x += dx.toInt(); params.y += dy.toInt(); runCatching { w.updateViewLayout(this, params) } },
                    onClose = { hideKeys() },
                )
            }
        }
        view = v; owner = o
        runCatching { w.addView(v, params) }.onFailure { view = null; o.stop(); owner = null }
    }

    private fun hideKeys() {
        val v = view ?: return
        runCatching { wm?.removeView(v) }
        owner?.stop()
        owner = null; view = null
    }

    /** The focused text field of the window in front, or null when the focus is not on a field. */
    private fun focusedField(): AccessibilityNodeInfo? =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }

    /** Replaces the selection of the focused field with [s] (typing a key). */
    fun commitText(s: String) = edit { cur, lo, hi -> (cur.substring(0, lo) + s + cur.substring(hi)) to (lo + s.length) }

    private fun deleteBefore() = edit { cur, lo, hi ->
        when {
            lo != hi -> (cur.removeRange(lo, hi)) to lo
            lo > 0 -> (cur.removeRange(lo - 1, lo)) to (lo - 1)
            else -> null
        }
    }

    private fun enter() {
        val node = focusedField() ?: return
        val done = Build.VERSION.SDK_INT >= 30 && node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        runCatching { node.recycle() }
        if (!done) commitText("\n")
    }

    private fun edit(change: (String, Int, Int) -> Pair<String, Int>?) {
        val node = focusedField() ?: return
        try {
            val cur = if (Build.VERSION.SDK_INT >= 26 && node.isShowingHintText) "" else node.text?.toString().orEmpty()
            val a = node.textSelectionStart.let { if (it < 0) cur.length else it.coerceAtMost(cur.length) }
            val b = node.textSelectionEnd.let { if (it < 0) a else it.coerceIn(a, cur.length) }
            val (text, pos) = change(cur, minOf(a, b), maxOf(a, b)) ?: return
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            val sel = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, pos)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, pos)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        } finally {
            runCatching { node.recycle() }
        }
    }

    companion object {
        /** The running service, while the accessibility service is on (the keys write through it). */
        @Volatile var instance: AccessKeyboardService? = null
    }
}

/** The keys: letters with Shift, a symbols page, space, backspace and enter. The bar on top is dragged; × closes. */
@Composable
private fun AccessKeys(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit,
) {
    var symbols by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    val letters = listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç")
    val marks = listOf("1234567890", "@#\$_&-+()/", "*\"':;!?,.")
    val rows = if (symbols) marks else letters
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xF2181A20)).padding(6.dp)) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).fillMaxSize().pointerInput(Unit) {
                    detectDragGestures { _, drag -> onDrag(drag.x, drag.y) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(64.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF6B7080)))
            }
            Text("×", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 12.dp))
        }
        rows.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (i == 2) KeyCap("⇧", Modifier.weight(1.5f), shift) { shift = !shift }
                r.forEach { ch ->
                    val s = if (shift && !symbols) ch.toString().uppercase(Locale("tr")) else ch.toString()
                    KeyCap(s, Modifier.weight(1f)) { onKey(s); if (shift) shift = false }
                }
                if (i == 2) KeyCap("⌫", Modifier.weight(1.5f)) { onBackspace() }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyCap(if (symbols) "ABC" else "?123", Modifier.weight(1.4f)) { symbols = !symbols }
            KeyCap(",", Modifier.weight(1f)) { onKey(",") }
            KeyCap("space", Modifier.weight(4f)) { onKey(" ") }
            KeyCap(".", Modifier.weight(1f)) { onKey(".") }
            KeyCap("↵", Modifier.weight(1.6f), accent = true) { onEnter() }
        }
    }
}

@Composable
private fun KeyCap(label: String, modifier: Modifier, on: Boolean = false, accent: Boolean = false, onTap: () -> Unit) {
    Box(
        modifier.height(42.dp).clip(RoundedCornerShape(8.dp))
            .background(if (accent) Color(0xFF3B82F6) else if (on) Color(0xFF5A6070) else Color(0xFF30333D))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
