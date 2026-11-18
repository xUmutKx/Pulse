package com.umutk.pulse.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.umutk.pulse.ContactsActivity
import com.umutk.pulse.R
import com.umutk.pulse.tr

/** One tap opens Pulse's Contacts app (a tile for the home screen and the lock screen list). */
class ContactsWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val open = Intent(ctx, ContactsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(ctx, 7_320, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return views(ctx, s, R.layout.widget_contacts, tr("Contacts", "Kişiler"), tr("Open", "Aç"))
            .apply { setOnClickPendingIntent(R.id.root, tap) }
    }
}
