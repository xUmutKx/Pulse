package com.umutk.pulse.widgets.music

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Picks the skin for one placed Winamp widget: the drawn default or any .wsz in filesDir/extras/skins. */
class WinampWidgetConfig : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val skins = com.umutk.pulse.extras.music.skin.installedSkins(this)
        fun pick(name: String?) {
            WinampWidget.prefs(this).edit().apply { if (name == null) remove("skin_$id") else putString("skin_$id", name) }.apply()
            AppWidgetManager.getInstance(this).updateAppWidget(id, WinampWidget.build(this, id))
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
        setContent {
            MaterialTheme { Surface {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    // shortcut: text rows, no live skin preview; add a render() thumbnail per row if wanted
                    Text("Winamp", Modifier.fillMaxWidth().clickable { pick(null) }.padding(vertical = 14.dp), style = MaterialTheme.typography.titleMedium)
                    skins.forEach { s ->
                        Text(s.name, Modifier.fillMaxWidth().clickable { pick(s.file.name) }.padding(vertical = 14.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } }
        }
    }
}
