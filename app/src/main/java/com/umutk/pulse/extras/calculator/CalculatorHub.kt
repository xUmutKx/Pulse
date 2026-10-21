package com.umutk.pulse.extras.calculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.features.Calc
import com.umutk.pulse.frame
import com.umutk.pulse.tr

/**
 * Calculator, drawn like the Windows one of the look it is in: Windows 11 and 10 flat keys with the accent "=", Material with round keys,
 * Windows 7 / XP / 95 with the raised grey keys, sunken white display and a menu bar. Standard keys, percent, memory and a history list.
 */
@Composable
fun CalculatorHub(back: () -> Unit) {
    var e by rememberSaveable { mutableStateOf("") }
    var done by rememberSaveable { mutableStateOf(false) }
    var err by remember { mutableStateOf(false) }
    var mem by remember { mutableDoubleStateOf(0.0) }
    var showHistory by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<String>() }
    val ops = "+−×÷"
    val classic = T.tabs && T.id != "win10"
    BackHandler { if (showHistory) showHistory = false else back() }

    fun num() = e.takeLastWhile { it.isDigit() || it == '.' }
    fun value(): Double? = if (e.isEmpty()) 0.0 else Calc.eval(e)
    fun set(v: Double?) { err = v == null; e = v?.let(Calc::show) ?: ""; done = v != null }
    fun press(k: String) {
        err = false
        if (done && (k[0].isDigit() || k == ".")) e = ""
        done = false
        val last = e.lastOrNull()
        when {
            k == "C" -> e = ""
            k == "CE" -> e = e.dropLast(num().length)
            k == "⌫" -> e = e.dropLast(1)
            k == "=" -> if (e.isNotEmpty()) { val v = Calc.eval(e); if (v != null) history.add(0, "$e = ${Calc.show(v)}"); set(v) }
            k == "±" -> value()?.let { set(-it) }
            k == "1/x" -> set(value()?.takeIf { it != 0.0 }?.let { 1 / it })
            k == "x²" -> set(value()?.let { it * it })
            k == "√" -> set(value()?.takeIf { it >= 0 }?.let { kotlin.math.sqrt(it) })
            k == "MC" -> mem = 0.0
            k == "MR" -> { e = Calc.show(mem); done = true }
            k == "M+" -> value()?.let { mem += it }
            k == "M−" -> value()?.let { mem -= it }
            e.length >= 40 -> {}
            k in ops -> e = when {
                last == null -> if (k == "−") k else e
                k == "−" && (last == '×' || last == '÷') -> e + k
                last in ops -> e.trimEnd { it in ops }.let { if (it.isEmpty()) (if (k == "−") k else "") else it + k }
                else -> e + k
            }
            k == "%" -> if (last != null && (last.isDigit() || last == '.')) e += "%"
            k == "." -> if ('.' !in num() && last != '%') e += if (num().isEmpty()) "0." else "."
            last == '%' -> {}
            num() == "0" -> e = e.dropLast(1) + k
            else -> e += k
        }
    }
    val preview = if (!done && e.drop(1).any { it in ops || it == '%' }) Calc.eval(e)?.let(Calc::show) else null
    val shown = if (err) tr("Error", "Hata") else e.ifEmpty { "0" }

    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).padding(horizontal = if (classic) 6.dp else 10.dp)) {
        // title row: back, name, history
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text(tr("Calculator", "Hesap makinesi"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clickable { showHistory = !showHistory }, contentAlignment = Alignment.Center) { PIcon("history", if (showHistory) Look.accent else T.text, 22.dp) }
            // the look of the calculator (Windows 11 / 10 / 7 / XP / 95, Material, AMOLED) is chosen here
            com.umutk.pulse.ExtraLookIcon(T.text)
        }
        if (showHistory) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (history.isEmpty()) Text(tr("No calculations yet", "Henüz işlem yok"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                history.forEach { h -> Text(h, color = T.text, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().clickable { e = h.substringAfter("= "); done = true; showHistory = false }.padding(12.dp)) }
            }
            return@Column
        }
        // display
        Column(
            Modifier.fillMaxWidth().then(if (classic) Modifier.background(Color.White).frame(T, raised = false) else Modifier).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(preview?.let { "= $it" } ?: if (mem != 0.0) "M" else " ", color = if (classic) Color.Black else T.sub, fontSize = 16.sp, maxLines = 1)
            Text(shown, color = if (classic) Color.Black else T.text, fontSize = if (shown.length > 12) 30.sp else 46.sp, fontWeight = FontWeight.Light, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(6.dp))
        val rows = if (classic) listOf(
            listOf("MC", "MR", "M+", "M−"), listOf("CE", "C", "⌫", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("±", "0", ".", "="),
        ) else listOf(
            listOf("MC", "MR", "M+", "M−"), listOf("%", "CE", "C", "⌫"), listOf("1/x", "x²", "√", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("±", "0", ".", "="),
        )
        val gap = if (classic) 4.dp else if (T.id == "win10") 2.dp else if (T.id == "amoled" || T.id == "material") 8.dp else 3.dp
        Column(Modifier.weight(1f).fillMaxWidth().widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { k ->
                        if (T.id == "amoled" || T.id == "material") {
                            // round keys stay round: a square as big as the cell allows, centred (a stretched circle is a thin pill)
                            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                val side = minOf(maxWidth, maxHeight)
                                CalcKey(k, Modifier.size(side), classic) { press(k) }
                            }
                        } else CalcKey(k, Modifier.weight(1f), classic) { press(k) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** One key in the look of the window: every Windows look has its own key, Material and AMOLED have round ones. */
@Composable
private fun CalcKey(k: String, modifier: Modifier, classic: Boolean, onClick: () -> Unit) {
    val equals = k == "="
    val digit = k.length == 1 && (k[0].isDigit() || k == "." || k == "±")
    val op = k in setOf("÷", "×", "−", "+")
    val mem = k.startsWith("M")
    val acc = Look.accent
    val id = T.id
    val round = id == "amoled" || id == "material"
    val shape: androidx.compose.ui.graphics.Shape = when {
        round -> androidx.compose.foundation.shape.CircleShape
        id == "win11" -> RoundedCornerShape(6.dp)
        id == "win7" || id == "xp" -> RoundedCornerShape(3.dp)
        else -> RoundedCornerShape(0.dp)
    }
    // fill: a brush for the glossy Windows 7 and XP keys, a colour for the rest
    val brush: androidx.compose.ui.graphics.Brush = when (id) {
        "win7" -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE9EEF5), Color(0xFFD3DCE8), Color(0xFFE1E8F1)))
        "xp" -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF5F4EA), Color(0xFFECE9D8), Color(0xFFD6D0C0)))
        else -> {
            val c = when {
                id == "win95" -> Color(0xFFC0C0C0)
                id == "amoled" -> when { equals -> acc; op -> Color(0xFF2A2A2A); digit -> Color(0xFF151515); else -> Color(0xFF0B0B0B) }
                equals -> acc
                digit -> T.text.copy(alpha = .10f).compositeOver(T.panel)
                mem -> Color.Transparent
                else -> T.text.copy(alpha = .05f).compositeOver(T.panel)
            }
            androidx.compose.ui.graphics.SolidColor(c)
        }
    }
    val ink = when {
        equals -> com.umutk.pulse.onAccent(acc)
        id == "win95" || id == "win7" -> if (op || k in setOf("C", "CE", "⌫")) Color(0xFFB00000) else if (mem) Color(0xFFB00000) else Color.Black
        id == "xp" -> if (op || k in setOf("C", "CE", "⌫", "±") || mem) Color(0xFFCC0000) else Color(0xFF0A246A)
        id == "amoled" -> if (op) Color(0xFFFFB74D) else if (mem) Color(0xFF9E9E9E) else Color.White
        mem -> T.sub
        else -> T.text
    }
    val border = when (id) { "win7" -> Color(0xFF8C99AD); "xp" -> Color(0xFF003C74); "amoled" -> Color(0xFF2A2A2A); else -> null }
    Box(
        modifier.fillMaxWidth().clip(shape).background(brush)
            .then(if (id == "win95") Modifier.frame(T, raised = true) else if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(k, color = ink, fontSize = if (mem) 14.sp else 20.sp, fontWeight = if (digit || id == "xp") FontWeight.SemiBold else FontWeight.Normal) }
}

private fun Color.compositeOverBg(): Color = compositeOver(T.panel)
