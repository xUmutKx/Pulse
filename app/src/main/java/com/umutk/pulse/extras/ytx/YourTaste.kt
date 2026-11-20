package com.umutk.pulse.extras.ytx

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** "Your taste": who the engine thinks you are, the shape of your taste against the time of day, what it learned, and the switches to correct it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YourTasteScreen(onBack: () -> Unit, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val c = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)
    // the numbers are read again after each lesson
    val rev = Neuro.rev
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        BackScreenHeader(onBack = onBack) {
            Text(tr("Your taste", "Zevkin"), fontFamily = RoundedHeavy, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.onSurface)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = contentPadding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val persona = remember(rev) { Neuro.persona() }
            val mature = remember(rev) { Neuro.maturity() }
            val profile = remember(rev) { Neuro.traits() }
            val now = remember(rev) { Neuro.nowTraits() }

            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScallopBadge(persona.icon)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr(persona.en, persona.tr), fontFamily = RoundedHeavy, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.onSurface)
                        Text(tr(persona.descEn, persona.descTr), color = c.onSurfaceVariant, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(tr("Profile maturity", "Profil olgunluğu"), color = c.onSurface, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("${(mature * 100).toInt()}%", color = c.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Meter(mature, null)
            }

            Label(tr("Taste shape", "Zevk şekli"))
            Card {
                Radar(profile, now)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(c.onSurface))
                    Text("  " + tr("Profile", "Profil") + "      ", color = c.onSurface, fontSize = 14.sp)
                    Box(Modifier.size(14.dp).clip(CircleShape).background(c.onSurfaceVariant.copy(alpha = .55f)))
                    Text("  " + tr("Now", "Şimdi"), color = c.onSurface, fontSize = 14.sp)
                }
                Text(tr("Your profile against what you watch at this time of day", "Profilin, günün bu saatinde izlediklerinle karşılaştırılmış"), color = c.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            listOf(
                Triple(tr("Pacing", "Tempo"), profile.pacing, now.pacing),
                Triple(tr("Complexity", "Karmaşıklık"), profile.complexity, now.complexity),
                Triple(tr("Duration preference", "Süre tercihi"), profile.duration, now.duration),
                Triple(tr("Live affinity", "Canlı yayın ilgisi"), profile.live, now.live),
                Triple(tr("Topic breadth", "Konu genişliği"), profile.breadth, now.breadth),
            ).forEach { (name, p, n) ->
                Card {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, color = c.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Meter(p, n)
                        }
                        Text("  ${(p * 100).toInt()}%", color = c.onSurfaceVariant, fontSize = 14.sp)
                    }
                    Text(tr("Now", "Şimdi") + " ${(n * 100).toInt()}%", color = c.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            val interests = remember(rev) { Neuro.interests() }
            Label(tr("Interests", "İlgi alanları"))
            Card {
                if (interests.isEmpty()) Text(tr("Watch and search for a while and your interests appear here.", "Bir süre izle ve ara, ilgi alanların burada görünsün."), color = c.onSurfaceVariant, fontSize = 14.sp)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    interests.forEach { i -> TopicChip(i.topic, i.weight / (interests.first().weight.coerceAtLeast(1f))) }
                }
            }

            val chans = remember(rev) { Neuro.favouriteChannels() }
            if (chans.isNotEmpty()) {
                Label(tr("Channels you like", "Sevdiğin kanallar"))
                Card {
                    chans.forEachIndexed { i, ch ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ch.name, color = c.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Meter(ch.weight / chans.first().weight.coerceAtLeast(1f), null, Modifier.width(80.dp))
                            Text("  " + tr("Forget", "Unut"), color = c.primary, fontSize = 13.sp, modifier = Modifier.clickable { Neuro.forgetChannel(ch.key) }.padding(6.dp))
                        }
                    }
                }
            }

            val blocked = remember(rev) { Neuro.blockedTopics() }
            if (blocked.isNotEmpty()) {
                Label(tr("Hidden topics", "Gizlenen konular"))
                Card {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        blocked.forEach { b ->
                            Box(Modifier.clip(RoundedCornerShape(50)).background(c.surfaceContainerHighest).clickable { Neuro.unblock(b) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text("$b  ✕", color = c.onSurface, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Label(tr("Data", "Veri"))
            Card {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Pause learning", "Öğrenmeyi duraklat"), color = c.onSurface, fontSize = 17.sp)
                        Text(tr("Nothing new is remembered; what is known still orders the feed", "Yeni bir şey hatırlanmaz; bilinenler akışı sıralamaya devam eder"), color = c.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Switch(Neuro.paused, { Neuro.setPaused(it) })
                }
                Spacer(Modifier.height(10.dp))
                if (!confirmReset) Text(tr("Reset taste", "Zevki sıfırla"), color = c.error, fontSize = 16.sp, modifier = Modifier.clickable { confirmReset = true }.padding(vertical = 8.dp))
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Forget everything learned?", "Öğrenilen her şey unutulsun mu?"), color = c.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(tr("Reset", "Sıfırla"), color = c.error, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { Neuro.reset(); confirmReset = false }.padding(10.dp))
                    Text(tr("Cancel", "Vazgeç"), color = c.primary, modifier = Modifier.clickable { confirmReset = false }.padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun Label(t: String) {
    Text(t, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(18.dp), content = content)
}

/** A bar for a share between 0 and 1; [now] puts a dot at the other value (the time of day). */
@Composable
private fun Meter(value: Float, now: Float?, modifier: Modifier = Modifier.fillMaxWidth()) {
    val c = MaterialTheme.colorScheme
    val fg = c.onSurface; val track = c.surfaceContainerHighest; val dot = c.onSurfaceVariant
    Canvas(modifier.height(10.dp)) {
        val h = size.height
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2), size = size)
        drawRoundRect(fg, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2), size = androidx.compose.ui.geometry.Size((size.width * value.coerceIn(0f, 1f)).coerceAtLeast(h), h))
        if (now != null) drawCircle(dot, h * .38f, Offset((size.width * now.coerceIn(0f, 1f)).coerceIn(h / 2, size.width - h / 2), h / 2))
    }
}

/** The persona's badge: a scalloped disc with its emoji. */
@Composable
private fun ScallopBadge(icon: String) {
    val col = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path()
            val r0 = size.minDimension / 2f * .9f; val amp = size.minDimension / 2f * .08f
            for (i in 0..360) {
                val a = i * PI / 180.0
                val r = r0 + amp * cos(a * 8).toFloat()
                val x = center.x + (r * cos(a)).toFloat(); val y = center.y + (r * sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, col)
        }
        Text(icon, fontSize = 38.sp)
    }
}

/** Five axes (pacing, complexity, duration, live, breadth), the profile filled and the time of day outlined. */
@Composable
private fun Radar(profile: Neuro.Traits, now: Neuro.Traits) {
    val c = MaterialTheme.colorScheme
    val grid = c.outlineVariant; val fill = c.onSurface; val line = c.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(300.dp)) {
        val cx = size.width / 2; val cy = size.height / 2; val r = size.minDimension / 2 * .92f
        fun pt(i: Int, v: Float): Offset { val a = -PI / 2 + i * 2 * PI / 5; return Offset(cx + (r * v * cos(a)).toFloat(), cy + (r * v * sin(a)).toFloat()) }
        for (ring in 1..4) {
            val p = Path(); for (i in 0..4) { val o = pt(i, ring / 4f); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }; p.close()
            drawPath(p, grid, style = Stroke(1.dp.toPx()))
        }
        for (i in 0..4) drawLine(grid, Offset(cx, cy), pt(i, 1f), 1.dp.toPx())
        fun poly(t: Neuro.Traits): Path {
            val v = listOf(t.pacing, t.complexity, t.duration, t.live, t.breadth).map { (.06f + it * .94f).coerceIn(0f, 1f) }
            val p = Path(); v.forEachIndexed { i, x -> val o = pt(i, x); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }; p.close(); return p
        }
        val pp = poly(profile); val np = poly(now)
        drawPath(np, line.copy(alpha = .35f)); drawPath(np, line.copy(alpha = .8f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(pp, fill.copy(alpha = .2f)); drawPath(pp, fill, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** An interest: tapped, it can be forgotten or hidden for good. */
@Composable
private fun TopicChip(topic: String, strength: Float) {
    val c = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Column {
        Box(Modifier.clip(RoundedCornerShape(50)).background(c.surfaceContainerHighest.copy(alpha = .5f + .5f * strength.coerceIn(0f, 1f))).clickable { open = !open }.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(topic, color = c.onSurface, fontSize = 14.sp)
        }
        if (open) Row {
            Text(tr("Forget", "Unut"), color = c.primary, fontSize = 12.sp, modifier = Modifier.clickable { Neuro.forget(topic) }.padding(6.dp))
            Text(tr("Hide", "Gizle"), color = c.error, fontSize = 12.sp, modifier = Modifier.clickable { Neuro.block(topic) }.padding(6.dp))
        }
    }
}
