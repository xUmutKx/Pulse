package com.umutk.pulse.features

// Notification history viewer. In-memory only (capped at MAX_ENTRIES, gone when the process dies).
//
// MANIFEST ENTRY NEEDED (not added here, goes inside <application> in AndroidManifest.xml):
//   <service
//       android:name="com.umutk.pulse.features.NotifLog$Listener"
//       android:exported="true"
//       android:label="Pulse notification log"
//       android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
//       <intent-filter>
//           <action android:name="android.service.notification.NotificationListenerService" />
//       </intent-filter>
//   </service>

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.DateFormat
import java.util.Date

object NotifLog {
    private const val MAX_ENTRIES = 500

    data class Entry(val pkg: String, val app: String, val title: String, val time: Long)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private fun add(e: Entry) {
        _entries.update { (listOf(e) + it).take(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun isGranted(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    // Declared in the manifest, see the header comment. Receives every posted notification
    // from the system; Pulse's own notifications are skipped.
    class Listener : NotificationListenerService() {
        override fun onNotificationPosted(sbn: StatusBarNotification) {
            if (sbn.packageName == packageName) return
            val extras = sbn.notification.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: return
            if (title.isBlank()) return
            val app = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                sbn.packageName
            }
            add(Entry(sbn.packageName, app, title, sbn.postTime))
        }
    }
}

@Composable
fun NotifLogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(NotifLog.isGranted(ctx)) }

    // Re-check the permission whenever the screen comes back to the foreground,
    // since the user usually grants it in system settings.
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = NotifLog.isGranted(ctx)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val all by NotifLog.entries.collectAsState()
    val groups = remember(all) { all.groupBy { it.pkg }.values.toList() }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Notifications", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (granted && all.isNotEmpty()) {
                IconButton(onClick = { NotifLog.clear() }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear")
                }
            }
        }

        if (!granted) {
            Text(
                "Notification access is needed to read posted notifications. Nothing is saved to disk.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            Button(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text("Open notification access") }
            return@Column
        }

        if (all.isEmpty()) {
            Text(
                "No notifications yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            groups.forEach { group ->
                appGroup(group, timeFormat)
            }
        }
    }
}

private fun LazyListScope.appGroup(group: List<NotifLog.Entry>, timeFormat: DateFormat) {
    item(key = "h:${group.first().pkg}") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)
        ) {
            Text(
                group.first().app,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                group.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    group.forEach { e ->
        item(key = "${e.pkg}:${e.time}:${e.title.hashCode()}") {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    timeFormat.format(Date(e.time)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    e.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider()
        }
    }
}
