package com.umutk.pulse.extras.lyrics

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Track

/**
 * The lyrics on the lock screen. The lyric notification (LiveLyricsTicker) opens this as its full-screen intent while
 * the phone is locked, so it shows over the lock screen without any root. It follows the song line by line and closes
 * itself once the phone is unlocked or the music stops.
 */
class LyricsLockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { LockLyrics(onFinish = { finishAndRemoveTask() }) }
    }
}

@Composable
private fun LockLyrics(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val engine = remember { MusicPlayerEngine.get(ctx) }
    val keyguard = remember { ctx.getSystemService(KeyguardManager::class.java) }
    val power = remember { ctx.getSystemService(PowerManager::class.java) }
    val state by engine.state.collectAsState()
    var position by mutableLongStateOf(0L)
    // the phone was locked when the view opened, so it closes only after an unlock (not at once on an open screen)
    var seenLocked by mutableStateOf(false)

    LaunchedEffect(Unit) {
        while (true) {
            position = engine.positionMs.toLong()
            @Suppress("DEPRECATION")
            val locked = keyguard.isKeyguardLocked || !power.isInteractive
            if (locked) seenLocked = true
            if ((seenLocked && !locked) || state.current == null) { onFinish(); return@LaunchedEffect }
            delay(250)
        }
    }

    val track: Track? = state.current
    val result by produceState<LyricsResult?>(null, track?.path) {
        value = track?.let { LiveLyrics.resultFor(ctx, it) }
    }
    val line = LiveLyrics.lineAt(result, position.toInt() + 700)

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (line != null) {
            Text(line.first.orEmpty(), color = Color.White.copy(alpha = .35f), fontSize = 22.sp, textAlign = TextAlign.Center)
            Text(line.second, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(line.third.orEmpty(), color = Color.White.copy(alpha = .35f), fontSize = 22.sp, textAlign = TextAlign.Center)
        }
    }
}
