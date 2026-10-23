package com.umutk.pulse

import android.app.NotificationChannel

/** A notification channel that never puts a badge (dot or count) on the app's icon: Pulse's notifications are status, not unread mail. */
@androidx.annotation.RequiresApi(26)
fun QuietChannel(id: String, name: CharSequence, importance: Int): NotificationChannel =
    NotificationChannel(id, name, importance).apply { setShowBadge(false) }
