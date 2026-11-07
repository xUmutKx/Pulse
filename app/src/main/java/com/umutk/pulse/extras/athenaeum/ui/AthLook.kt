package com.umutk.pulse.extras.athenaeum.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.umutk.pulse.Themes
import com.umutk.pulse.extras.athenaeum.FeatureSettings
import com.umutk.pulse.tr

/** Athenaeum's own look: Material, the Windows looks or AMOLED black. It does not touch the look of Pulse. */
@Composable
fun AthLookPicker() {
    val ctx = LocalContext.current
    val cur = FeatureSettings.look.value
    val ids = Themes.map { it.id to it.name } + ("amoled" to "AMOLED")
    Text(tr("Look", "Görünüm"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp))
    Column(Modifier.fillMaxWidth()) {
        ids.forEach { (id, name) ->
            Row(Modifier.fillMaxWidth().clickable { FeatureSettings.setLook(ctx, id) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = cur == id, onClick = { FeatureSettings.setLook(ctx, id) })
                Text(name, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
