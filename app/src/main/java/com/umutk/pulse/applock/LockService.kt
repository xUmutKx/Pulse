package com.umutk.pulse.applock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.umutk.pulse.Cfg
import com.umutk.pulse.Opt
import com.umutk.pulse.features.AppLock
import com.umutk.pulse.tr

/**
 * App Lock: watches which app is in front (accessibility events) and covers it with the lock screen (LockScreen.kt).
 * The screen is a TYPE_ACCESSIBILITY_OVERLAY window: it needs no "Display over other apps" permission and eats every tap.
 * No foreground service: the accessibility service is kept alive by the system.
 */
class LockService : AccessibilityService() {
    private var current: String? = null     // app in front, as last seen
    private var root: FrameLayout? = null   // the lock screen, built for cardPkg
    private var shown = false               // root is added to the window manager
    private var cardPkg: String? = null     // app the screen is for
    private var authing = false             // the biometric screen is up, so the lock screen is taken off
    private var authSeen = false            // AppLockAuthActivity really started
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val main = Handler(Looper.getMainLooper())
    private val isActivity = HashMap<String, Boolean>()
    private var t0 = 0L                     // elapsedRealtime of the event that said a locked app is in front (timing log)
    private var trig = ""
    private var spec: String? = null        // app covered on a launcher tap, not yet seen in front
    private var fastWatch: FastWatch? = null
    private var tick = 0
    private val labels = HashMap<String, String>()   // lower-case app label -> locked package
    private var labelsFor: Set<String>? = null

    private val kgm by lazy { getSystemService(android.app.KeyguardManager::class.java) }
    /** The phone's own lock screen is up: nothing of ours may show over it. */
    private fun kgUp() = runCatching { kgm.isKeyguardLocked }.getOrDefault(false)
    private val closedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()   // wall-clock time of the last "closed" relock, so a replayed old DESTROYED event is ignored

    private fun mark(src: String, ageMs: Long = 0) { t0 = SystemClock.elapsedRealtime() - ageMs; trig = src }

    /** Run now when on the main thread, otherwise at the front of its queue. */
    private fun fast(block: () -> Unit) { if (Looper.myLooper() === Looper.getMainLooper()) block() else main.postAtFrontOfQueue { runCatching(block).onFailure { Log.e("AppLock", "fast failed", it) } } }

    private fun syncFast() {
        val want = AppLock.fast && AppLock.locked.isNotEmpty() && (com.umutk.pulse.Shell.root == true || com.umutk.pulse.Shell.shizukuOk())
        if (want && fastWatch == null) {
            fastWatch = FastWatch { pkg, cls -> fastEvent(pkg, cls) }.also { it.start() }
            Log.d("AppLock", "fast mode on")
        } else if (!want && fastWatch != null) { fastWatch?.stop(); fastWatch = null; Log.d("AppLock", "fast mode off") }
    }

    private fun fastEvent(pkg: String, cls: String) {
        val t = SystemClock.elapsedRealtime()
        fast {
            if (pkg == packageName || pkg in transient || kgUp()) return@fast
            if (cls == AppLockAuthActivity::class.java.name || cls == LockActivity::class.java.name) return@fast
            if (pkg in AppLock.locked) { mark("root", SystemClock.elapsedRealtime() - t); Log.d("AppLock", "root resumed $pkg/$cls (queue ${SystemClock.elapsedRealtime() - t}ms)") }
            lastPolled = pkg
            onForeground(pkg)
        }
    }

    /** Label of a locked app -> package, rebuilt when the locked set changes. */
    private fun labelMap(): Map<String, String> {
        if (labelsFor != AppLock.locked) {
            labels.clear()
            for (p in AppLock.locked) runCatching { labels[packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString().lowercase()] = p }
            labelsFor = AppLock.locked
        }
        return labels
    }

    /** A tap in the launcher or recents: the icon's label says which app is about to open, so the lock goes up before its window. */
    private fun tap(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg !in never || AppLock.locked.isEmpty()) return
        val src = runCatching { e.source }.getOrNull()
        val names = e.text.map { it.toString() } + listOfNotNull(e.contentDescription?.toString(), src?.text?.toString(), src?.contentDescription?.toString())
        val map = labelMap()
        val hit = names.firstNotNullOfOrNull { n -> val k = n.trim().lowercase(); map[k] ?: map.entries.firstOrNull { k.startsWith(it.key + ",") || k.startsWith(it.key + " ") && k.length < it.key.length + 20 }?.value }
        runCatching { src?.recycle() }
        if (hit == null || hit in never || hit == current || !AppLock.needsAsk(hit, System.currentTimeMillis())) return
        mark("tap", SystemClock.uptimeMillis() - e.eventTime)
        Log.d("AppLock", "tap on launcher -> $hit (event age ${SystemClock.uptimeMillis() - e.eventTime}ms)")
        showCard(hit, auth = false)
        if (cardPkg == hit) {
            spec = hit
            // a tap that did not open the app (folder, widget, name clash): take the guess back
            main.postDelayed({ if (spec == hit) { Log.d("AppLock", "tap guess wrong, removed"); hideCard() } }, 700)
        }
    }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // Screen off relocks everything; the next window change of a locked app asks again.
            // Keyguard gone: look again at the app that was open (it was ignored while the keyguard showed).
            if (i.action == Intent.ACTION_USER_PRESENT) { lastPolled = null; current?.let { onForeground(it) }; main.postDelayed({ recheck() }, 300); main.postDelayed({ recheck() }, 1200); return }
            // Phone without a keyguard: waking shows the app at once, so a locked one is covered right away.
            if (i.action == Intent.ACTION_SCREEN_ON) { if (!kgUp()) current?.let { if (it in AppLock.locked) onForeground(it) }; return }
            // Screen off relocks everything. Nothing is shown now: the keyguard comes up and our window must not sit on it. Covered again on unlock / screen on.
            val c = current
            AppLock.relockAll()
            lastPolled = null
            hideCard()
            if (c == null || c !in AppLock.locked || c in never) current = null
        }
    }

    /** Looks at what is in front right now (no event came: wake-up, service start after boot or process death). */
    private fun recheck() {
        if (kgUp()) return
        runCatching {
            val pkg = rootInActiveWindow?.packageName?.toString()
                ?: windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }.firstNotNullOfOrNull { it.root?.packageName?.toString() }
                ?: return
            if (pkg == packageName || pkg == "com.android.systemui" || pkg in transient) return
            Log.d("AppLock", "recheck front $pkg")
            lastPolled = pkg
            onForeground(pkg)
            if (shown && cardPkg == pkg && bioOk && !authing) startAuth(pkg)
        }.onFailure { Log.e("AppLock", "recheck failed", it) }
    }

    private var viaActivity = false         // the lock screen is hosted by LockActivity (overlay window was refused)
    @Volatile private var lastPolled: String? = null  // last app the usage-stats poll saw come to the front
    private val never by lazy {             // never locked: Pulse itself, the launcher(s), system UI
        val home = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }
        (home + listOf(packageName, "com.android.systemui", "android")).toSet()
    }
    // dialogs of the system that sit over an app: not "leaving" it, or a permission prompt would relock the app under it
    private val transient = setOf("com.google.android.permissioncontroller", "com.android.permissioncontroller", "android")
    private val poll = object : Runnable {
        private var missing = false
        override fun run() { runCatching { if (tick++ % 10 == 0) syncFast(); if (shown && !authing && kgUp()) hideCard(); if (tick % 3 == 0) scanWindows(); missing = prebuild() }.onFailure { Log.e("AppLock", "poll failed", it) }; main.postDelayed(this, if (missing) 40L else 200L) }
    }
    // the usage-stats query is a binder call: it runs on its own thread so it never holds up an accessibility event on the main thread
    private val bg by lazy { Handler(android.os.HandlerThread("applock-poll").apply { start() }.looper) }
    private val bgPoll = object : Runnable {
        override fun run() { runCatching { pollOnce(); closedCheck() }.onFailure { Log.e("AppLock", "poll failed", it) }; bg.postDelayed(this, 120) }
    }

    private val ready = HashMap<String, FrameLayout>()   // lock screens built ahead, one per locked app
    private var readyRev = -1
    private var bioOk = false

    private var readySig = ""
    // the ready screens depend on the lock settings, the app theme and the day/night mode: any change rebuilds them
    private fun sig() = "${AppLock.rev}/${AppLock.look}/${com.umutk.pulse.Look.theme.id}/${com.umutk.pulse.features.LockWall.sig(this, AppLock.lookTheme().id)}/${com.umutk.pulse.Look.mode}/${com.umutk.pulse.Look.accent}/${resources.configuration.uiMode}"
    private fun sync() { val g = sig(); if (readyRev != AppLock.rev || readySig != g) { ready.clear(); readyRev = AppLock.rev; readySig = g; bioOk = bioReady() } }

    private fun build(pkg: String): FrameLayout {
        lateinit var v: FrameLayout
        val unlock = { s: String -> AppLock.checkSecret(s).also { if (it) { AppLock.markUnlocked(pkg); hideCard() } } }
        val bio = if (bioOk) ({ startAuth(pkg) }) else null
        // back to the home screen first, the lock screen stays until the launcher is up (or 0.7 s) so the app is never seen
        val cancel = { performGlobalAction(GLOBAL_ACTION_HOME); main.postDelayed({ if (root === v) hideCard() }, 700); Unit }
        v = runCatching { lockScreen(this, pkg, unlock, bio, cancel) }
            .getOrElse { Log.e("AppLock", "lock screen build failed, using the Material one", it); lockScreen(this, pkg, unlock, bio, cancel, look = "material") }
        return v
    }

    /** One missing lock screen per call (never a burst on the main thread), so the hot path only has to add a ready view. */
    private fun prebuild(): Boolean {
        sync()
        val p = AppLock.locked.firstOrNull { it !in ready && it !in never } ?: return false
        val v = build(p)
        // laid out ahead: the first frame after addView needs no measure pass of its own
        runCatching { val b = wm.maximumWindowMetrics.bounds
            v.measure(android.view.View.MeasureSpec.makeMeasureSpec(b.width(), android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(b.height(), android.view.View.MeasureSpec.EXACTLY))
            v.layout(0, 0, b.width(), b.height()) }
        ready[p] = v
        return true
    }

    private val destroyed = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var lastProc = 0L

    /**
     * "Until closed" mode: an unlocked app is relocked only when it was closed.
     * With root/Shizuku: its process is gone (pidof), checked every 3 s.
     * Without: shortcut: an ACTIVITY_DESTROYED usage event with no ACTIVITY_RESUMED of that app within 1.5 s counts as closed.
     * That misses a swipe-away that leaves no destroy event and relocks when the app's last screen is finished with Back; upgrade by using root/Shizuku.
     */
    private fun closedCheck() {
        if (AppLock.graceMin >= 0) return
        val un = AppLock.unlockedPkgs().filter { it in AppLock.locked }
        if (un.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (kgUp()) return
        if (com.umutk.pulse.Shell.root == true || com.umutk.pulse.Shell.shizukuOk()) {
            if (now - lastProc < 3000) return
            lastProc = now
            val out = com.umutk.pulse.Shell.run("echo ok; for p in ${un.joinToString(" ")}; do pidof \$p >/dev/null && echo \$p; done")
            if (!out.lines().any { it.trim() == "ok" }) return   // shell failed: do not relock on no information
            val alive = out.lines().map { it.trim() }.toSet()
            for (p in un) if (p !in alive && p != current) { Log.d("AppLock", "process gone, relock $p"); closedAt[p] = System.currentTimeMillis(); AppLock.relock(p) }
        } else {
            for ((p, at) in destroyed) if (now - at >= 1500) { destroyed.remove(p); if (p != current) { Log.d("AppLock", "activity destroyed, relock $p"); closedAt[p] = System.currentTimeMillis(); AppLock.relock(p) } }
        }
    }

    /** Second detection path, independent of accessibility events: the newest "activity resumed" from usage stats. */
    private fun pollOnce() {
        if (AppLock.locked.isEmpty() || !AppLock.usageGranted(this) || !getSystemService(PowerManager::class.java).isInteractive || kgUp()) return
        val now = System.currentTimeMillis()
        val ev = getSystemService(UsageStatsManager::class.java).queryEvents(now - 3000, now)
        val e = UsageEvents.Event()
        var pkg: String? = null; var cls = ""
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            // RESUMED == MOVE_TO_FOREGROUND (both 1); the same event is replayed for 3 s, so it is the last one that counts
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) { pkg = e.packageName; cls = e.className ?: ""; destroyed.remove(e.packageName) }
            else if (e.eventType == 24 /* ACTIVITY_DESTROYED, hidden constant */ && e.timeStamp > (closedAt[e.packageName] ?: 0L)) destroyed.putIfAbsent(e.packageName, SystemClock.elapsedRealtime())
        }
        if (pkg == null || pkg == lastPolled || pkg == packageName || pkg in transient) return
        val p: String = pkg
        fast {
            if (p == lastPolled) return@fast
            lastPolled = p
            Log.d("AppLock", "poll front $p/$cls")
            if (p != current) { if (p in AppLock.locked) mark("poll"); onForeground(p) }
        }
    }

    companion object {
        @Volatile var instance: LockService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // the system may start this process for the service alone: load the lock, the theme and the language here too
        AppLock.load(this); Cfg.load(this); Opt.load(this)
        readyRev = -1
        main.removeCallbacks(poll); main.post(poll)
        bg.removeCallbacks(bgPoll); bg.post(bgPoll)
        runCatching { unregisterReceiver(screen) }
        ContextCompat.registerReceiver(this, screen, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_ON) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        main.postDelayed({ recheck() }, 500)   // the app in front when the service (re)starts has sent no event
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        main.removeCallbacks(poll)
        bg.removeCallbacks(bgPoll)
        fastWatch?.stop(); fastWatch = null
        runCatching { unregisterReceiver(screen) }
        hideCard()
        return super.onUnbind(intent)
    }

    override fun onDestroy() { fastWatch?.stop(); fastWatch = null; super.onDestroy() }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (kgUp()) return   // the phone's lock screen (and notifications on it) never triggers ours
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) { runCatching { tap(e) }.onFailure { Log.e("AppLock", "tap failed", it) }; return }
        if (e.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) { scanWindows(); return }
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        runCatching { handle(e) }.onFailure { Log.e("AppLock", "event failed", it) }   // an exception here would kill the service process
    }

    private fun handle(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        val cls = e.className?.toString() ?: ""
        if (cls == AppLockAuthActivity::class.java.name || cls == LockActivity::class.java.name) return
        // the recents screen is system UI on some phones: leaving the app there relocks it, so coming back from recents asks again
        if (pkg == "com.android.systemui") { if (cls.contains("Recents", ignoreCase = true)) onForeground(pkg) else scanWindows(); return }
        if (pkg in transient) return
        // hot path: a locked app that must ask is covered at once, before any window lookup (the later size check removes it for a floating window)
        if (pkg in AppLock.locked && pkg !in never && AppLock.needsAsk(pkg, System.currentTimeMillis())) {
            // a chat bubble (a small floating window) opens without the lock unless Settings > App lock > Bubbles is on
            if (!AppLock.lockBubbles && floating(pkg)) return
            mark("a11y-hot", SystemClock.uptimeMillis() - e.eventTime)
            Log.d("AppLock", "a11y hot $pkg/$cls (event age ${SystemClock.uptimeMillis() - e.eventTime}ms)")
            onForeground(pkg)
            return
        }
        // Only an application window changes the app in front: the keyboard, toasts and our own overlay do not
        // (the keyboard opening for the PIN field used to count as "left the app" and took the lock screen away).
        // The window type is used rather than a class lookup: a class that is no activity name (aliases, hosts) used to be dropped, so the lock never came.
        val win = runCatching { windows.firstOrNull { it.id == e.windowId } }.getOrNull()
        if (win != null) { if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION) return }
        else if (!isActivity.getOrPut("$pkg/$cls") { runCatching { packageManager.getActivityInfo(ComponentName(pkg, cls), 0) }.isSuccess }) return
        if (pkg in AppLock.locked) mark("a11y", SystemClock.uptimeMillis() - e.eventTime)
        Log.d("AppLock", "front $pkg/$cls locked=${pkg in AppLock.locked}")
        onForeground(pkg)
    }

    private var lastScan = 0L
    private var cardAt = 0L   // uptime the current lock screen went up
    private val rescan = Runnable { scanWindows() }

    /**
     * Chat bubbles, floating windows and pop-ups of a locked app are windows of any type (a bubble is hosted by system UI):
     * every window's own package is looked at, not just the one the last event named. A locked app seen in any window is covered;
     * a card whose app has no window left (bubble collapsed) is taken off. Throttled: each window root is a binder call.
     */
    private fun scanWindows() {
        if (AppLock.locked.isEmpty() || kgUp()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastScan < 120) { main.removeCallbacks(rescan); main.postDelayed(rescan, 130); return }   // a throttled event is retried, not lost
        lastScan = now
        runCatching {
            val ws = windows
            var allKnown = true
            val seen = HashSet<String>()
            for (w in ws) {
                if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD || w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                val p = w.root?.packageName?.toString()
                if (p == null) { if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) allKnown = false; continue }
                seen += p
            }
            // an unlocked app whose last window (bubble) is gone has now been left: its grace time starts
            if (allKnown) for (p in AppLock.locked) if (p !in seen && (p != current || SystemClock.uptimeMillis() - cardAt > 1500)) AppLock.leave(p)
            val hit = seen.firstOrNull { it in AppLock.locked && it !in never && it !in transient && it != packageName && AppLock.needsAsk(it, System.currentTimeMillis()) && (AppLock.lockBubbles || !floating(it)) }
            if (hit != null) {
                if (hit != cardPkg) {
                    mark("windows"); Log.d("AppLock", "window of locked app $hit (bubble or floating)")
                    spec = null; showCard(hit)
                    current = hit   // a bubble opened while another app was in front: it is now the app being used
                }
            } else if (!AppLock.lockBubbles && shown && !authing && cardPkg != null && cardPkg in seen && floating(cardPkg!!)) { Log.d("AppLock", "$cardPkg is only a bubble, lock removed"); hideCard()
            } else if (allKnown && shown && !authing && cardPkg != null && (cardPkg != current || SystemClock.uptimeMillis() - cardAt > 1500) && cardPkg !in seen) { Log.d("AppLock", "window of $cardPkg gone, lock removed"); hideCard() }
        }.onFailure { Log.e("AppLock", "window scan failed", it) }
    }

    /** The app only shows in a small window (chat bubble, floating window). Opening the fingerprint host would collapse a bubble, so it is not started by itself then. */
    private fun floating(pkg: String): Boolean = runCatching {
        val b = wm.maximumWindowMetrics.bounds; val r = android.graphics.Rect(); var any = false
        for (w in windows) {
            if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD || w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY || w.root?.packageName?.toString() != pkg) continue
            w.getBoundsInScreen(r)
            if (r.width().toLong() * r.height() >= b.width().toLong() * b.height() * 8 / 10) return false
            any = true
        }
        any
    }.getOrDefault(false)

    private fun hasWindow(pkg: String): Boolean = runCatching {
        windows.any { w -> w.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD && w.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && w.root?.packageName?.toString() == pkg }
    }.getOrDefault(false)

    private fun onForeground(pkg: String) {
        lastPolled = pkg   // else reopening the app the poll saw last (after an event-only visit elsewhere) was taken for "already in front"
        if (spec != null && pkg in never) return   // the launcher's own events during the first 0.7 s must not take the guessed lock away
        if (pkg != current) {
            // an app that still has a window (chat bubble, floating window) is not left yet
            current?.let { if (!hasWindow(it)) AppLock.leave(it) }
            current = pkg
        }
        if (!AppLock.lockBubbles && pkg in AppLock.locked && floating(pkg)) { spec = null; hideCard(); return }
        if (pkg !in never && AppLock.needsAsk(pkg, System.currentTimeMillis())) {
            val guessed = spec == pkg && shown && cardPkg == pkg
            spec = null
            showCard(pkg)
            if (guessed && bioOk && !authing && !floating(pkg)) startAuth(pkg)   // covered by the guess, the fingerprint sheet follows now the app is confirmed
        } else { spec = null; hideCard() }
    }

    private fun bioReady() = AppLock.useBio &&
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showCard(pkg: String, auth: Boolean = true) {
        if (kgUp()) { if (shown) hideCard(); return }
        if (cardPkg != pkg) {
            hideCard()
            sync()
            root = ready.remove(pkg) ?: runCatching { build(pkg) }.onFailure { Log.e("AppLock", "build failed", it) }.getOrNull() ?: return
            cardPkg = pkg
            cardAt = SystemClock.uptimeMillis()
            attach(true)   // covered first, the fingerprint sheet is asked for afterwards
            if (bioOk && auth && !floating(pkg)) startAuth(pkg)
            return
        }
        if (!authing) attach(true)
    }

    /** Called when the biometric screen closes: an unlocked app loses the lock screen, otherwise it comes back. */
    fun onAuthEnded(pkg: String) {
        authing = false
        if (pkg != current || !AppLock.needsAsk(pkg, System.currentTimeMillis())) { hideCard(); return }
        attach(true)
    }

    /** The lock screen as a picture, for the fingerprint host to show under the system sheet. Null when it is not laid out. */
    fun snapshot(): android.graphics.Bitmap? = runCatching {
        val v = root ?: return null
        if (v.width <= 0 || v.height <= 0) return null
        android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.RGB_565).also { v.draw(android.graphics.Canvas(it)) }
    }.onFailure { Log.e("AppLock", "snapshot failed", it) }.getOrNull()

    // the overlay would sit above the system fingerprint sheet; it comes off only once the host shows the lock picture
    fun onAuthShown() { authSeen = true; attach(false) }

    private fun startAuth(pkg: String) {
        if (authing && authSeen) return   // the prompt is already up: a second tap on the fingerprint button must not stack another
        authing = true
        authSeen = false
        runCatching { startActivity(Intent(this, AppLockAuthActivity::class.java)
            .putExtra(AppLockAuthActivity.EXTRA_PKG, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)) }.onFailure { Log.e("AppLock", "auth activity start failed", it) }
        // a blocked start would leave the app open: bring the lock screen back if the prompt never came up
        main.postDelayed({ if (authing && !authSeen && cardPkg == pkg) onAuthEnded(pkg) }, 1500)
    }

    private fun attach(on: Boolean) {
        val v = root ?: return
        if (on == shown) return
        if (on) {
            val a = SystemClock.elapsedRealtime()
            shown = addOverlay(v)
            if (shown) {
                if (t0 != 0L) {
                    val src = trig; val t = t0; t0 = 0L
                    Log.d("AppLock", "overlay added via $src: addView ${SystemClock.elapsedRealtime() - a}ms, ${SystemClock.elapsedRealtime() - t}ms since trigger")
                    v.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean { v.viewTreeObserver.removeOnPreDrawListener(this); Log.d("AppLock", "first draw via $src: ${SystemClock.elapsedRealtime() - t}ms since trigger"); return true }
                    })
                }
                focusInput(v)
            }
            return
        }
        if (viaActivity) LockActivity.cur?.finish() else runCatching { wm.removeView(v) }.onFailure { Log.e("AppLock", "overlay remove failed", it) }
        viaActivity = false
        shown = false
    }

    /** Accessibility overlay first, then a normal overlay (needs "display over other apps"), then a full-screen activity. */
    private fun addOverlay(v: FrameLayout): Boolean {
        runCatching { wm.addView(v, overlayParams(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)) }.onSuccess { return true }.onFailure { Log.e("AppLock", "a11y overlay failed", it) }
        if (com.umutk.pulse.Compat.canOverlay(this))
            runCatching { wm.addView(v, overlayParams(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)) }.onSuccess { return true }.onFailure { Log.e("AppLock", "app overlay failed", it) }
        viaActivity = runCatching { startActivity(Intent(this, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)) }
            .onFailure { Log.e("AppLock", "lock activity failed", it) }.isSuccess
        return viaActivity
    }

    /** Puts the cursor in the PIN/password field and opens the keyboard (a window added by the service gets neither by itself). */
    private fun focusInput(v: android.view.View) {
        fun edit(x: android.view.View): android.widget.EditText? = if (x is android.widget.EditText) x
            else (x as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { edit(g.getChildAt(it)) } }
        val e = edit(v) ?: return
        main.postDelayed({
            if (shown && root === v && !authing) {
                e.requestFocus()
                getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showSoftInput(e, 0)
            }
        }, 350)
    }

    /** The lock screen view for LockActivity. */
    fun lockView(): FrameLayout? = root

    private fun hideCard() {
        attach(false)
        root = null
        cardPkg = null
        authing = false
        spec = null
    }

    private fun overlayParams(type: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
        type, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.OPAQUE,
    ).apply { windowAnimations = 0; softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN }
}

/**
 * Transparent host for the BiometricPrompt, started by LockService. BiometricPrompt needs an Activity;
 * the lock screen is taken off while this runs so the system sheet is not covered.
 */
class AppLockAuthActivity : FragmentActivity() {
    private var pkg = ""
    private var ended = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: run { finish(); return }
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            != BiometricManager.BIOMETRIC_SUCCESS) { LockService.instance?.onAuthShown(); finish(); return }   // no fingerprint: the lock screen asks for the PIN or password
        // the lock screen stays visible under the fingerprint sheet: this window shows its picture, edge to edge, and the
        // overlay is taken off only after this window has drawn, so the locked app is never seen in between
        window.setWindowAnimations(0)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        val pic = LockService.instance?.snapshot()
        setContentView(android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_XY
            if (pic != null) setImageBitmap(pic) else setBackgroundColor(android.graphics.Color.BLACK)
        })
        window.decorView.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                window.decorView.postDelayed({ if (!ended) LockService.instance?.onAuthShown() }, 32)
                return true
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(tr("App locked", "Uygulama kilitli"))
            .setSubtitle(AppLock.label(this, pkg))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(when (AppLock.secretKind) { "pin" -> "PIN"; "pattern" -> tr("Pattern", "Desen"); else -> tr("Password", "Şifre") })
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AppLock.markUnlocked(pkg)
                end()
            }

            // Cancel, negative button, lockout: the lock screen goes back up before this window closes.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { end() }
        }).authenticate(info)
    }

    private fun end() {
        if (!ended) { ended = true; LockService.instance?.onAuthEnded(pkg) }
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (pkg.isNotEmpty() && !ended) { ended = true; LockService.instance?.onAuthEnded(pkg) }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}

/** Fallback host of the lock screen: shown over the locked app when no overlay window can be added. Back goes home. */
class LockActivity : android.app.Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        cur = this
        val v = LockService.instance?.lockView() ?: run { finish(); return }
        (v.parent as? android.view.ViewGroup)?.removeView(v)
        setContentView(v)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { LockService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }

    override fun onDestroy() {
        super.onDestroy()
        if (cur === this) cur = null
        (findViewById<android.view.View>(android.R.id.content) as? android.view.ViewGroup)?.removeAllViews()
    }

    companion object { @Volatile var cur: LockActivity? = null }
}
