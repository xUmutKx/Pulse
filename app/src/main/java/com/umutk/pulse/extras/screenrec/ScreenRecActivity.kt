package com.umutk.pulse.extras.screenrec

import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Cfg
import com.umutk.pulse.LookHost
import com.umutk.pulse.Opt
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/** Small window over the app: asks for the system consent and starts the recording, or controls it. The tile opens it with "auto". */
class ScreenRecActivity : ComponentActivity() {
    private var recState by mutableStateOf(RecState.IDLE)
    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { askConsent() }
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) { ScreenRecService.start(this, r.resultCode, data, cfg()); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        if (savedInstanceState == null && intent.getBooleanExtra("shot", false)) {
            // wait for the Quick Settings shade to slide away, take the picture, then close
            android.os.Handler(mainLooper).postDelayed({
                if (!ScreenShot.possible()) { android.widget.Toast.makeText(this, tr("Needs root or Shizuku", "Root veya Shizuku gerekir"), android.widget.Toast.LENGTH_LONG).show(); finish() }
                else ScreenShot.take(this) { p -> android.widget.Toast.makeText(this, if (p != null && p.startsWith("blank:")) tr("Saved, but the app blocks it (black)", "Kaydedildi ama uygulama engelliyor (siyah)") else if (p != null) tr("Screenshot saved", "Ekran görüntüsü kaydedildi") else tr("Could not take the screenshot", "Ekran görüntüsü alınamadı"), android.widget.Toast.LENGTH_SHORT).show(); finish() }
            }, 700)
            return
        }
        if (savedInstanceState == null && intent.getBooleanExtra("auto", false) && !ScreenRecService.running) begin()
        setContent { LookHost(transparent = true) { Panel() } }
    }

    override fun onResume() { super.onResume(); recState = ScreenRecService.state }

    private fun cfg() = ScreenRecPrefs.config(this)

    /** The microphone is asked first when it is switched on; a refusal still records the screen without sound. */
    private fun begin() {
        if (cfg().mic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        else askConsent()
    }

    private fun askConsent() {
        consent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }

    @Composable
    private fun Panel() {
        var mic by remember { mutableStateOf(ScreenRecPrefs.mic(this)) }
        // follows the service while the window is open, so the buttons match a recording started elsewhere
        LaunchedEffect(Unit) { while (true) { recState = ScreenRecService.state; delay(500) } }
        val recording = recState != RecState.IDLE
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(horizontal = pageGutter).widthIn(max = 360.dp).fillMaxWidth().background(T.bg).border(1.dp, T.line).padding(20.dp)) {
                Text(tr("Screen recorder", "Ekran kaydı"), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon("sensors", if (recState == RecState.RUNNING) T.accent else T.sub)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when (recState) { RecState.RUNNING -> tr("Recording", "Kaydediyor"); RecState.PAUSED -> tr("Paused", "Duraklatıldı"); else -> tr("Ready", "Hazır") },
                        color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f)
                    )
                    if (recording) Text(ScreenRecService.formatElapsed(ScreenRecService.elapsedMs()), color = T.sub, fontSize = 14.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clickable(enabled = !recording) { mic = !mic; ScreenRecPrefs.setMic(this@ScreenRecActivity, mic) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("volume", T.text)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("Microphone", "Mikrofon"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(if (mic) tr("On", "Açık") else tr("Off", "Kapalı"), color = T.sub, fontSize = 14.sp)
                }
                Spacer(Modifier.height(16.dp))
                if (!recording) PButton(text = tr("Start", "Başlat"), modifier = Modifier.fillMaxWidth()) { begin() }
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    PButton(text = if (recState == RecState.PAUSED) tr("Resume", "Devam") else tr("Pause", "Duraklat"), modifier = Modifier.weight(1f)) {
                        if (recState == RecState.PAUSED) ScreenRecService.resume(this@ScreenRecActivity) else ScreenRecService.pause(this@ScreenRecActivity)
                    }
                    Spacer(Modifier.width(10.dp))
                    PButton(text = tr("Stop", "Durdur"), modifier = Modifier.weight(1f), danger = true) { ScreenRecService.stop(this@ScreenRecActivity) }
                }
            }
        }
    }
}
