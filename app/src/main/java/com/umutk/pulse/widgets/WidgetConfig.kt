package com.umutk.pulse.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Opened when a widget is added or reconfigured: pick a style, see the real widget in it, then Done. */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className
        val kind = WidgetData.all.firstOrNull { it.javaClass.name == provider }
        if (kind == null) { finish(); return }

        setContent {
            val chosen = remember { mutableStateOf(WidgetStyles.of(this, widgetId)) }
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Widget style", style = MaterialTheme.typography.titleLarge)
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        factory = { FrameLayout(it) },
                        update = { frame ->
                            // the real widget, rendered in the chosen style; the overlay stops taps from opening Pulse
                            frame.removeAllViews()
                            kind.content(this@WidgetConfigActivity, chosen.value).apply(this@WidgetConfigActivity, frame)
                            frame.addView(View(this@WidgetConfigActivity).apply { setOnClickListener {} })
                        },
                    )
                    WidgetStyle.values().toList().chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { s ->
                                FilterChip(
                                    selected = chosen.value == s,
                                    onClick = { chosen.value = s },
                                    label = { Text(s.label) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { save(kind, chosen.value) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Done") }
                }
            }
        }
    }

    private fun save(kind: PulseWidget, s: WidgetStyle) {
        WidgetStyles.save(this, widgetId, s)
        AppWidgetManager.getInstance(this).updateAppWidget(widgetId, kind.content(this, s))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
