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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
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
        // the lock screen keeps the keyboard (the PIN) and every touch (the unlock swipe): this window only shows the lyrics
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        // a strip of text, not a full window: the width of the screen, its height is the text, at the bottom above the shortcuts
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(android.view.Gravity.BOTTOM)
        // a failure here must not hold the lock screen: the view closes instead
        runCatching { setContent { LockLyrics(onFinish = { finishAndRemoveTask() }) } }.onFailure { finishAndRemoveTask() }
    }
}

@Composable
private fun LockLyrics(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val engine = remember { MusicPlayerEngine.get(ctx) }
    val keyguard = remember { ctx.getSystemService(KeyguardManager::class.java) }
    val power = remember { ctx.getSystemService(PowerManager::class.java) }
    val state by engine.state.collectAsState()
    // remembered: without remember a new state is made on every recomposition, the loop writes to an old one and the line never moves
    var position by remember { mutableLongStateOf(0L) }
    // the phone was locked when the view opened, so it closes only after an unlock (not at once on an open screen)
    var seenLocked by remember { mutableStateOf(false) }

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
        // no background at all: the lock screen and the always-on clock show through, the lines have a soft shadow to stay readable
        // lifted to the height of the wallpaper's peak (about a third up from the bottom); the dark haze behind the lines keeps white text readable over snow
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 270.dp)
            .background(Color.Black.copy(alpha = .5f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .9f), Offset(0f, 2f), 10f))
        // one line only, in the same size and weight as the player's lyrics (the setting's size plus 4, bold)
        val size = com.umutk.pulse.extras.music.MusicSettings.lyricsFont(ctx).coerceIn(12, 30) + 4
        if (line != null) {
            Text(line.second, color = Color.White, fontSize = size.sp, lineHeight = (size * 1.3f).sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, style = shadow)
            // the next line under it, in a darker grey, as the player shows its neighbours
            line.third?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White.copy(alpha = .4f), fontSize = (size - 6).sp, lineHeight = ((size - 6) * 1.3f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = shadow)
            }
        } else if (track != null) {
            // no line yet: the song's name, so the view is never an empty strip
            Text(track.title ?: track.path.substringAfterLast('/'), color = Color.White.copy(alpha = .7f), fontSize = size.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = shadow)
        }
    }
}
