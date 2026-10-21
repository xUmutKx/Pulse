package com.umutk.pulse.extras.screenrec

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import com.umutk.pulse.R
import com.umutk.pulse.tr

/** Records the screen to Movies/Pulse Recordings as MP4. [start] takes the consent result of the system dialog.
 *  FLAG_SECURE windows come out black; nothing here overrides that. */
class ScreenRecService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var overlay: CaptureOverlay? = null
    private var touchesWas: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopRecording() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val i = intent
        when (i?.action) {
            ACTION_STOP -> { stopRecording(); return START_NOT_STICKY }
            ACTION_PAUSE -> { pauseRecording(); return START_NOT_STICKY }
            ACTION_RESUME -> { resumeRecording(); return START_NOT_STICKY }
        }
        // startForeground must run on every start that came through startForegroundService, even when it is rejected below
        startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        if (state != RecState.IDLE) return START_NOT_STICKY
        val data = i?.let { extraIntent(it) }
        if (i == null || data == null) { stopSelf(); return START_NOT_STICKY }
        val cfg = RecConfig(
            mic = i.getBooleanExtra(EXTRA_MIC, false),
            quality = i.getIntExtra(EXTRA_QUALITY, ScreenRecPrefs.QUALITY_MEDIUM),
            fps = i.getIntExtra(EXTRA_FPS, 30),
            limitMin = i.getIntExtra(EXTRA_LIMIT, 0),
        )
        val audio = cfg.mic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        try {
            val mp = getSystemService(MediaProjectionManager::class.java).getMediaProjection(i.getIntExtra(EXTRA_CODE, 0), data)
            projection = mp
            mp.registerCallback(object : MediaProjection.Callback() { override fun onStop() = stopRecording() }, main)
            startRecording(mp, cfg, audio)
            runStart = SystemClock.elapsedRealtime(); accum = 0L
            state = RecState.RUNNING
            if (cfg.limitMin > 0) main.postDelayed(autoStop, cfg.limitMin * 60_000L)
            updateNotification()
            if (ScreenRecPrefs.touches(this)) showTouches(true)
            if (ScreenRecPrefs.floating(this)) overlay = CaptureOverlay(this, { if (state == RecState.RUNNING) pauseRecording() else resumeRecording() }, { stopRecording() }, { grabFrame() }).also { it.show() }
        } catch (e: Exception) {
            Toast.makeText(this, tr("Recording could not start", "Kayıt başlatılamadı"), Toast.LENGTH_LONG).show()
            stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(mp: MediaProjection, cfg: RecConfig, audio: Boolean) {
        val dm = resources.displayMetrics
        val (w, h) = cfg.size(dm.widthPixels, dm.heightPixels)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "pulse_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, RecordingList.RELATIVE_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val u = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore refused the file")
        uri = u
        val fd = contentResolver.openFileDescriptor(u, "w") ?: error("no output file")
        pfd = fd
        val rec = MediaRecorder(this)
        recorder = rec
        rec.apply {
            if (audio) setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(fd.fileDescriptor)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(w, h)
            setVideoFrameRate(cfg.fps)
            setVideoEncodingBitRate(cfg.bitrate(w, h))
            if (audio) { setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioSamplingRate(44_100); setAudioEncodingBitRate(128_000) }
            prepare()
        }
        display = mp.createVirtualDisplay("PulseRecording", w, h, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rec.surface, null, null)
        rec.start()
    }

    private fun pauseRecording() {
        if (state != RecState.RUNNING) return
        runCatching { recorder?.pause() }
        accum += SystemClock.elapsedRealtime() - runStart
        state = RecState.PAUSED
        overlay?.setPaused(true)
        updateNotification()
    }

    private fun resumeRecording() {
        if (state != RecState.PAUSED) return
        runCatching { recorder?.resume() }
        runStart = SystemClock.elapsedRealtime()
        state = RecState.RUNNING
        overlay?.setPaused(false)
        updateNotification()
    }

    /** A still picture of the screen while the video runs (screencap, so it needs root or Shizuku). */
    private fun grabFrame() {
        if (!ScreenShot.possible()) { Toast.makeText(this, tr("Needs root or Shizuku", "Root veya Shizuku gerekir"), Toast.LENGTH_SHORT).show(); return }
        ScreenShot.take(this) { p -> Toast.makeText(this, if (p != null && p.startsWith("blank:")) tr("Saved, but the app blocks it (black)", "Kaydedildi ama uygulama engelliyor (siyah)") else if (p != null) tr("Picture saved", "Görüntü kaydedildi") else tr("Could not take the picture", "Görüntü alınamadı"), Toast.LENGTH_SHORT).show() }
    }

    /** "Show touches" for the length of the recording; the old value comes back afterwards. */
    private fun showTouches(on: Boolean) {
        Thread {
            if (on) {
                touchesWas = com.umutk.pulse.Shell.run("settings get system show_touches").trim().ifEmpty { "0" }
                val c = "settings put system show_touches 1"
                if (com.umutk.pulse.Shell.root == true) com.umutk.pulse.Shell.runRoot(c) else com.umutk.pulse.Shell.run(c)
            } else {
                val c = "settings put system show_touches ${touchesWas ?: "0"}"
                if (com.umutk.pulse.Shell.root == true) com.umutk.pulse.Shell.runRoot(c) else com.umutk.pulse.Shell.run(c)
                touchesWas = null
            }
        }.start()
    }

    /** Idempotent: the projection's onStop calls it too. A row that never recorded is deleted, a finished one is published. */
    private fun stopRecording() {
        val wasRunning = state != RecState.IDLE
        if (state == RecState.RUNNING) accum += SystemClock.elapsedRealtime() - runStart
        state = RecState.IDLE
        overlay?.hide(); overlay = null
        if (touchesWas != null) showTouches(false)
        main.removeCallbacks(autoStop)
        val u = uri; uri = null
        val p = projection; projection = null
        val stopped = runCatching { recorder?.stop() }.isSuccess   // throws when nothing was written yet; the file is dropped below
        runCatching { recorder?.release() }
        recorder = null
        display?.release(); display = null
        p?.stop()
        runCatching { pfd?.close() }; pfd = null
        if (u != null) {
            if (wasRunning && stopped) {
                contentResolver.update(u, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                Toast.makeText(this, tr("Saved to Movies/Pulse Recordings", "Movies/Pulse Recordings klasörüne kaydedildi"), Toast.LENGTH_SHORT).show()
            } else runCatching { contentResolver.delete(u, null, null) }
        }
        @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() { stopRecording(); super.onDestroy() }

    /** getParcelableExtra(name, Class) is API 33; minSdk is 31. */
    private fun extraIntent(i: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") i.getParcelableExtra(EXTRA_DATA) as? Intent

    private fun updateNotification() = getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification())

    /** Status, live timer (counts only while recording) and the pause/resume and stop actions. */
    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, tr("Screen recording", "Ekran kaydı"), NotificationManager.IMPORTANCE_LOW))
        val paused = state == RecState.PAUSED
        val icon = Icon.createWithResource(this, R.drawable.ic_qs_pulse)
        val pauseIntent = PendingIntent.getService(this, 1, Intent(this, ScreenRecService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0, Intent(this, ScreenRecService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_qs_pulse)
            .setContentTitle(if (paused) tr("Recording paused", "Kayıt duraklatıldı") else tr("Recording the screen", "Ekran kaydediliyor"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(icon, if (paused) tr("Resume", "Devam") else tr("Pause", "Duraklat"), pauseIntent).build())
            .addAction(Notification.Action.Builder(icon, tr("Stop", "Durdur"), stop).build())
        if (paused) b.setUsesChronometer(false).setContentText(formatElapsed(elapsedMs()))
        else b.setUsesChronometer(true).setWhen(System.currentTimeMillis() - elapsedMs())
        return b.build()
    }

    companion object {
        private const val ACTION_STOP = "com.umutk.pulse.screenrec.STOP"
        private const val ACTION_PAUSE = "com.umutk.pulse.screenrec.PAUSE"
        private const val ACTION_RESUME = "com.umutk.pulse.screenrec.RESUME"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_MIC = "mic"
        private const val EXTRA_QUALITY = "quality"
        private const val EXTRA_FPS = "fps"
        private const val EXTRA_LIMIT = "limit"
        private const val CHANNEL = "screenrec"
        private const val NOTIF_ID = 4120

        @Volatile var state = RecState.IDLE
        @Volatile private var runStart = 0L
        @Volatile private var accum = 0L

        /** True from the moment the recording starts until it is saved (paused counts as running). */
        val running: Boolean get() = state != RecState.IDLE

        /** Recorded time, paused time excluded. */
        fun elapsedMs(): Long = accum + if (state == RecState.RUNNING) SystemClock.elapsedRealtime() - runStart else 0L

        /** [data] is the Intent from the consent dialog's result; the mic needs its permission granted before this. */
        fun start(c: Context, code: Int, data: Intent, cfg: RecConfig) {
            c.startForegroundService(Intent(c, ScreenRecService::class.java).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_MIC, cfg.mic).putExtra(EXTRA_QUALITY, cfg.quality).putExtra(EXTRA_FPS, cfg.fps).putExtra(EXTRA_LIMIT, cfg.limitMin))
        }

        fun stop(c: Context) { c.startService(Intent(c, ScreenRecService::class.java).setAction(ACTION_STOP)) }
        fun pause(c: Context) { c.startService(Intent(c, ScreenRecService::class.java).setAction(ACTION_PAUSE)) }
        fun resume(c: Context) { c.startService(Intent(c, ScreenRecService::class.java).setAction(ACTION_RESUME)) }

        fun formatElapsed(ms: Long): String {
            val s = ms / 1000
            val h = s / 3600; val m = (s / 60) % 60; val sec = s % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
        }
    }
}
