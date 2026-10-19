package com.umutk.pulse.widgets.music

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.umutk.pulse.extras.video.PalSwatch
import com.umutk.pulse.extras.video.VideoWidgetLooks

/** Picks one of the 50 looks for a placed video widget; each cell is a drawing of that look. */
class VideoWidgetConfig : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val cur = VideoWidget.lookPrefs(this).getInt("look_$id", 0)
        setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(VideoWidgetLooks) { i, l ->
                        Box(Modifier.border(if (i == cur) 2.dp else 0.dp, if (i == cur) Color(l.pal.accent) else Color.Transparent, RoundedCornerShape(12.dp)).padding(2.dp).clickable {
                            VideoWidget.lookPrefs(this@VideoWidgetConfig).edit().putInt("look_$id", i).apply()
                            AppWidgetManager.getInstance(this@VideoWidgetConfig).updateAppWidget(id, VideoWidget.build(this@VideoWidgetConfig, id))
                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        }) { PalSwatch(l.pal, if (l.square) 3 else 14, Modifier.padding(0.dp)) }
                    }
                }
            } }
        }
    }
}
