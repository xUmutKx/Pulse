package com.umutk.pulse

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.features.AppDetailsScreen
import com.umutk.pulse.features.BatteryHealthScreen
import com.umutk.pulse.features.CacheCleanerScreen
import com.umutk.pulse.features.FolderTreeScreen
import com.umutk.pulse.features.HealthScreen
import com.umutk.pulse.features.TempGraphScreen
import com.umutk.pulse.features.TypeBreakdownScreen

/** Full-screen tool, opened from the page that shows its row (see ToolLinks).  "app:<package>" opens the details of one app. */
@Composable
fun SubScreen(id: String, back: () -> Unit) {
    val root = Environment.getExternalStorageDirectory()
    when {
        id.startsWith("app:") -> AppDetailsScreen(id.removePrefix("app:"), back)
        id == "typebreak" -> TypeBreakdownScreen(root, back)
        id == "folders" -> FolderTreeScreen(root, back)
        id == "cache" -> CacheCleanerScreen(back)
        id == "tempgraph" -> TempGraphScreen(back)
        id == "battery" -> BatteryHealthScreen(back)
        id == "health" -> HealthScreen(back)
        id == "toolkit" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.ToolkitPage() }
        id == "installer" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.InstallerPage() }
        id == "installer_settings" -> {
            androidx.activity.compose.BackHandler { back() }
            Column(Modifier.fillMaxSize().background(T.bg).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                PHead("Installer")
                InstallWindowSettings()
            }
        }
        id == "ram" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.RamPage() }
        id == "dns" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.DnsSettings() }
        id == "troubleshoot" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.doctor.TroubleshootScreen(back) }
        id == "netusage" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.NetUsageScreen(back) }
        id == "authenticator" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.AuthenticatorScreen(back) }
    }
}
