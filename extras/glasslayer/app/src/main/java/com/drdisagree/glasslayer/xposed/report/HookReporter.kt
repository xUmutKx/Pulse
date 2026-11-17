package com.drdisagree.glasslayer.xposed.report

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.drdisagree.glasslayer.data.mapper.HookReportJsonMapper
import com.drdisagree.glasslayer.data.source.ReportContract
import com.drdisagree.glasslayer.domain.model.HookReport
import com.drdisagree.glasslayer.domain.model.ModReport
import com.drdisagree.glasslayer.xposed.glass.BackdropCapture
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.HostApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList

object HookReporter {

    private const val MAX_ERRORS = 30
    private const val MAX_LOGS = 80
    private const val MAX_FRAMES = 6
    private const val RETRY_MS = 1_000L
    private const val MAX_ATTEMPTS = 60
    private const val SEND_DEBOUNCE_MS = 1_000L
    private const val TIME_PATTERN = "HH:mm:ss.SSS"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mods = ConcurrentHashMap<String, Int>()
    private val modOrder = CopyOnWriteArrayList<String>()
    private val broken: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val errors = CopyOnWriteArrayList<String>()
    private val logs = ConcurrentLinkedDeque<String>()
    private val timeFormat = SimpleDateFormat(TIME_PATTERN, Locale.US)
    private val loadedAt = System.currentTimeMillis()
    private val sendRunnable = Runnable { send() }

    @Volatile
    private var framework = ""

    @Volatile
    private var registered = false

    @Volatile
    private var safe = false

    private var attempts = 0
    private var updatedAt = 0L

    private val requestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            send()
        }
    }

    fun safeMode(value: Boolean) {
        safe = value
    }

    fun framework(name: String) {
        framework = name
    }

    fun mod(name: String, hooks: Int) {
        if (name !in modOrder) modOrder += name
        mods[name] = hooks
    }

    fun broken(tag: String) {
        broken += tag
        scheduleSend()
    }

    fun log(message: String, throwable: Throwable? = null) {
        try {
            val time = synchronized(timeFormat) { timeFormat.format(Date()) }
            val level = if (throwable == null) "I" else "E"
            logs.addLast("$time $level $message" + (throwable?.let { " | " + describe(it) } ?: ""))
            while (logs.size > MAX_LOGS) logs.pollFirst()
            if (throwable != null) {
                if (errors.size < MAX_ERRORS) errors += message + ": " + describe(throwable)
                scheduleSend()
            }
        } catch (_: Throwable) {
        }
    }

    fun start() {
        mainHandler.post(::attach)
    }

    private fun attach() {
        try {
            val context = HostApplication.get()
            if (context == null) {
                if (++attempts < MAX_ATTEMPTS) mainHandler.postDelayed(::attach, RETRY_MS)
                return
            }
            if (!registered) {
                updatedAt = moduleUpdatedAt(context)
                context.registerReceiver(requestReceiver, IntentFilter(ReportContract.ACTION_REQUEST), Context.RECEIVER_EXPORTED)
                registered = true
            }
            send()
        } catch (_: Throwable) {
        }
    }

    private fun scheduleSend() {
        mainHandler.removeCallbacks(sendRunnable)
        mainHandler.postDelayed(sendRunnable, SEND_DEBOUNCE_MS)
    }

    private fun send() {
        try {
            val context = HostApplication.get() ?: return
            val report = HookReport(
                timestamp = System.currentTimeMillis(),
                framework = framework,
                systemUiVersion = systemUiVersion(context),
                buildId = Build.ID,
                moduleUpdatedAt = updatedAt,
                moduleLoadedAt = loadedAt,
                moduleApkPath = moduleApkPath(),
                uptimeMs = SystemClock.uptimeMillis() - Process.getStartUptimeMillis(),
                captureAvailable = BackdropCapture.isAvailable,
                mods = modOrder.map { name ->
                    ModReport(name, mods[name] ?: 0, broken.any { it.startsWith("$name.") || it.startsWith("$name:") })
                },
                broken = broken.toList(),
                errors = errors.toList(),
                logs = logs.toList(),
                safeMode = safe
            )
            context.sendBroadcast(
                Intent(ReportContract.ACTION_REPORT)
                    .setPackage(ReportContract.APP_PACKAGE)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(ReportContract.EXTRA_REPORT, HookReportJsonMapper.toJson(report))
            )
        } catch (_: Throwable) {
        }
    }

    private fun describe(throwable: Throwable): String = buildString {
        append(throwable.javaClass.name)
        throwable.message?.let { append(": ").append(it) }
        throwable.stackTrace.take(MAX_FRAMES).forEach { append(" @ ").append(it) }
        throwable.cause?.let { cause ->
            append(" <- ").append(cause.javaClass.name)
            cause.message?.let { append(": ").append(it) }
        }
    }

    private fun moduleApkPath(): String = try {
        HookEnv.module.moduleApplicationInfo.sourceDir.orEmpty()
    } catch (_: Throwable) {
        ""
    }

    private fun moduleUpdatedAt(context: Context): Long = try {
        context.packageManager.getPackageInfo(ReportContract.APP_PACKAGE, 0).lastUpdateTime
    } catch (_: Throwable) {
        0L
    }

    private fun systemUiVersion(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    } catch (_: Throwable) {
        ""
    }
}
