package com.umutk.pulse

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        id == "extrastore" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.ui.MaxWidthBox { com.umutk.pulse.extras.store.ExtraStoreScreen(onBack = back) } }
        id == "installer" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.InstallerPage() }
        id == "cover" -> {
            androidx.activity.compose.BackHandler { back() }
            Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                PHead(tr("Screen cover", "Ekran kapağı"))
                com.umutk.pulse.features.CoverTool()
            }
        }
        id == "installer_settings" -> {
            androidx.activity.compose.BackHandler { back() }
            Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                PHead("Installer")
                InstallWindowSettings()
            }
        }
        id == "files_settings" -> {
            androidx.activity.compose.BackHandler { back() }
            Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                PHead("Files")
                com.umutk.pulse.features.FilesSettings()
            }
        }
        id == "ram" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.RamPage() }
        id == "fonts" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.FontChangerScreen() }
        id == "dns" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.DnsSettings() }
        id == "troubleshoot" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.doctor.TroubleshootScreen(back) }
        id == "buds" -> com.umutk.pulse.extras.buds.BudsScreen(back)
        id == "speedtest" -> com.umutk.pulse.extras.speedtest.SpeedTestScreen(back)
        id == "mediatools" -> com.umutk.pulse.extras.mediatools.MediaToolsScreen(back)
        id == "netusage" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.NetUsageScreen(back) }
        id == "guide" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.GuidePage() }
        id == "authenticator" -> { androidx.activity.compose.BackHandler { back() }; com.umutk.pulse.features.AuthenticatorScreen(back) }
        id == "video" -> com.umutk.pulse.extras.video.VideoHub(back)
        id == "music" -> com.umutk.pulse.extras.music.MusicHub(back)
        id == "whitenoise" -> com.umutk.pulse.extras.whitenoise.WhiteNoiseHub(back)
        id == "voice" -> com.umutk.pulse.extras.voice.VoiceHub(back)
        id == "notes" -> com.umutk.pulse.extras.notes.NotesHub(back)
        id == "weather" -> com.umutk.pulse.extras.weather.WeatherHub(back)
        id == "paint" -> com.umutk.pulse.extras.paint.PaintHub(back)
        id == "calculator" -> com.umutk.pulse.extras.calculator.CalculatorHub(back)
        id == "localsend" -> com.umutk.pulse.extras.localsend.LocalSendHub(back)
        id == "palette" -> com.umutk.pulse.extras.palette.PaletteHub(back)
        id == "blackout" -> com.umutk.pulse.extras.blackout.BlackOutHub(back)
        id == "keyboard" -> com.umutk.pulse.extras.keyboard.KeyboardSettings(back)
        id == "notifhist" -> com.umutk.pulse.extras.notifhist.NotifHistoryScreen(back)
        id == "screenrec" -> com.umutk.pulse.extras.screenrec.ScreenRecHub(back)
        id == "playlists" -> com.umutk.pulse.extras.playlists.OnlinePlaylistsHub(back)
        id == "launcher" -> com.umutk.pulse.extras.launcher.LauncherSettings(back)
        id == "messages" -> MessagesScreen(back)
        // Pulse's own pages (Task Manager, This PC ...), for the launcher's windows
        id == "game:minesweeper" -> com.umutk.pulse.extras.launcher.MinesweeperGame()
        id == "game:hub" -> com.umutk.pulse.extras.launcher.GameHub()
        id == "game:freecell" -> com.umutk.pulse.extras.launcher.FreeCellGame()
        id == "game:solitaire" -> com.umutk.pulse.extras.launcher.SolitaireGame()
        id == "store:launcher" -> com.umutk.pulse.extras.launcher.LauncherStoreWindow()
        id == "page:filestabs" -> com.umutk.pulse.extras.launcher.ExplorerTabsWindow()
        id == "controlcenter" -> com.umutk.pulse.extras.launcher.ControlCenterWindow()
        id == "wpsettings" -> com.umutk.pulse.extras.launcher.PhoneSettingsHome()
        id == "controlpanel" -> com.umutk.pulse.extras.launcher.ControlPanelHome()
        id == "winsettings" -> com.umutk.pulse.extras.launcher.WindowsSettingsHome()
        id == "page:processes" -> ProcessesPage()
        id == "page:performance" -> PerformancePage()
        id == "page:history" -> HistoryPage()
        id == "page:startup" -> StartupPage()
        id == "page:details" -> DetailsPage()
        id == "page:services" -> ServicesPage()
        id == "page:apps" -> AppsPage()
        id == "page:device" -> DeviceModule()
        id == "page:settings" -> SettingsPage()
        id == "page:recycle" -> com.umutk.pulse.features.RecycleBinPage()
        id == "page:terminal" -> com.umutk.pulse.features.TerminalPage()
        id == "page:files" -> com.umutk.pulse.features.FilesPage()
    }
}

