package com.umutk.pulse.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.umutk.pulse.ExtraToolActivity
import com.umutk.pulse.R
import com.umutk.pulse.tr

/** One tap starts a take in the voice recorder; the page starts recording itself (see VoiceHub autostart). */
class VoiceWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val open = Intent(ctx, ExtraToolActivity::class.java)
            .putExtra("route", "voice").putExtra("autostart", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(ctx, 7_310, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return views(ctx, s, R.layout.widget_voice, tr("Voice recorder", "Ses kaydedici"), tr("Record", "Kaydet"))
            .apply { setOnClickPendingIntent(R.id.root, tap) }
    }
}
