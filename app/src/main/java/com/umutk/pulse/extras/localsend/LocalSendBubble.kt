package com.umutk.pulse.extras.localsend

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.umutk.pulse.features.CoverAccessibilityService
import com.umutk.pulse.features.StandByOwner
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A transfer of LocalSend (sending or receiving) is shown as a notification, not as something floating over the apps: the name of the other device, a progress
 * bar, and Pause / Cancel in it. It is looked at twice a second and goes a moment after the transfer ends.
 */
object LocalSendBubble {
    private var job: kotlinx.coroutines.Job? = null
    val showing get() = job != null
    private const val CHANNEL = "localsend_transfer"
    private const val ID = 7421

    /** Starts the notification of the transfer; it is always possible (the name stays from the old bubble: callers ask whether something could be shown). */
    fun show(ctx: Context): Boolean {
        if (job != null) return true
        val app = ctx.applicationContext
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, tr("File transfer", "Dosya aktarımı"), android.app.NotificationManager.IMPORTANCE_LOW))
        job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            var quiet = 0
            while (true) {
                val busy = LocalSendEngine.busy
                if (busy.isEmpty()) { quiet++; if (quiet > 3) break; delay(500); continue }
                quiet = 0
                runCatching { nm.notify(ID, build(app, busy, LocalSendEngine.progress, LocalSendEngine.paused)) }
                delay(500)
            }
            runCatching { nm.cancel(ID) }
            job = null
        }
        return true
    }

    fun hide() {
        job?.cancel(); job = null
        runCatching { (host() ?: return).cancel(ID) }
    }
    private var nmRef: android.app.NotificationManager? = null
    private fun host() = nmRef

    private fun build(app: Context, busy: String, p: Float, paused: Boolean): android.app.Notification {
        nmRef = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        fun act(a: String) = android.app.PendingIntent.getBroadcast(app, a.hashCode(), Intent(app, LocalSendActionReceiver::class.java).setAction(a),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (android.os.Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(app, CHANNEL) else @Suppress("DEPRECATION") android.app.Notification.Builder(app)
        val incoming = busy.startsWith("←")
        return b.setSmallIcon(if (incoming) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_upload)
            .setContentTitle(busy)
            .setContentText("${(p * 100).toInt()}%" + if (paused) " · " + tr("paused", "duraklatıldı") else "")
            .setProgress(100, (p * 100).toInt(), false)
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.app.Notification.Action.Builder(null, if (paused) tr("Resume", "Sürdür") else tr("Pause", "Duraklat"), act("ls_pause")).build())
            .addAction(android.app.Notification.Action.Builder(null, tr("Cancel", "İptal"), act("ls_cancel")).build())
            .build()
    }
}

/** Pause and Cancel in the notification of a transfer. */
class LocalSendActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) { "ls_pause" -> LocalSendEngine.paused = !LocalSendEngine.paused; "ls_cancel" -> LocalSendEngine.cancel() }
    }
}
