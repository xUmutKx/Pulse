package com.umutk.pulse.extras.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umutk.pulse.extras.ai.cc.BuddySettings
import com.umutk.pulse.extras.ai.cc.Prefs

/** The settings of the floating mascot (Claude Chat's own settings list: size, tap, sleep, speech bubble ...). */
@Composable
internal fun AiBuddySettingsDialog(onClose: () -> Unit) {
    Prefs.init(LocalContext.current)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF161616)).verticalScroll(rememberScrollState()).padding(20.dp)) {
                BuddySettings()
            }
        }
    }
}
