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
import androidx.compose.foundation.layout.fillMaxHeight
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
    var mode by rememberSaveable { mutableStateOf("standard") }
    var menu by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<String>() }
    val ops = "+−×÷"
    val classic = T.tabs && T.id != "win10"
    BackHandler { if (showHistory) showHistory = false else if (mode != "standard") mode = "standard" else back() }

    // a sum with brackets goes through the scientific parser, the plain ones through the simple one
    fun ev(x: String): Double? = if ('(' in x || ')' in x) com.umutk.pulse.extras.calculator.Sci.eval(x) else Calc.eval(x)
    fun num() = e.takeLastWhile { it.isDigit() || it == '.' }
    fun value(): Double? = if (e.isEmpty()) 0.0 else ev(e)
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
            k == "=" -> if (e.isNotEmpty()) { val v = ev(e); if (v != null) history.add(0, "$e = ${Calc.show(v)}"); set(v) }
            k == "±" -> value()?.let { set(-it) }
            k == "1/x" -> set(value()?.takeIf { it != 0.0 }?.let { 1 / it })
            k == "x²" -> set(value()?.let { it * it })
            k == "√" -> set(value()?.takeIf { it >= 0 }?.let { kotlin.math.sqrt(it) })
            k == "MC" -> mem = 0.0
            k == "MR" -> { e = Calc.show(mem); done = true }
            k == "M+" -> value()?.let { mem += it }
            k == "M−" -> value()?.let { mem -= it }
            e.length >= 40 -> {}
            // the bracket key: closes a bracket that is open, else opens one (a number before it is multiplied)
            k == "( )" -> {
                val open = e.count { it == '(' } - e.count { it == ')' }
                val afterNum = last != null && (last.isDigit() || last == ')' || last == '%')
                e += if (open > 0 && afterNum) ")" else if (afterNum) "×(" else "("
            }
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
    val preview = if (!done && e.drop(1).any { it in ops || it == '%' || it == '(' }) ev(e.trimEnd { it in ops || it == '(' })?.let(Calc::show) else null
    val round = T.id == "material" || T.id == "amoled"
    val shown = if (err) tr("Error", "Hata") else e.ifEmpty { "0" }

    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).padding(horizontal = if (classic) 6.dp else 10.dp)) {
        // title row: back, the mode's name, history (standard) and the menu (the other modes and the look of the window)
        if (round && mode == "standard") Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { showHistory = !showHistory }, contentAlignment = Alignment.Center) { PIcon("history", if (showHistory) Look.accent else T.text, 24.dp) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(44.dp).clickable { mode = "convert"; showHistory = false }, contentAlignment = Alignment.Center) { PIcon("scale", T.text, 24.dp) }
            Box(Modifier.size(44.dp).clickable { mode = "scientific"; showHistory = false }, contentAlignment = Alignment.Center) { PIcon("function", T.text, 24.dp) }
            Box(Modifier.size(44.dp).clickable { menu = true }, contentAlignment = Alignment.Center) { PIcon("menu", T.text, 24.dp) }
        } else Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text(modeTitle(mode), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (mode == "standard") Box(Modifier.size(40.dp).clickable { showHistory = !showHistory }, contentAlignment = Alignment.Center) { PIcon("history", if (showHistory) Look.accent else T.text, 22.dp) }
            Box(Modifier.size(40.dp).clickable { menu = true }, contentAlignment = Alignment.Center) { PIcon("menu", T.text, 22.dp) }
        }
        if (menu) CalcMenu(mode, { mode = it; showHistory = false; menu = false }) { menu = false }
        if (mode != "standard") {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (mode) { "scientific" -> SciPane(classic); "graph" -> GraphPane(); "convert" -> ConvertPane(); else -> ProgrammerPane(classic) }
            }
            return@Column
        }
        if (showHistory) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (history.isEmpty()) Text(tr("No calculations yet", "Henüz işlem yok"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                history.forEach { h -> Text(h, color = T.text, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().clickable { e = h.substringAfter("= "); done = true; showHistory = false }.padding(12.dp)) }
            }
            return@Column
        }
        if (round) {
            // the Material calculator: a roomy display (the earlier sum or the answer so far above, the number being typed below) and round keys of a sensible size
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
                Text(preview?.let { "= $it" } ?: history.firstOrNull() ?: " ", color = T.sub, fontSize = 20.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                Text(shown, color = T.text, fontSize = if (shown.length > 12) 40.sp else 64.sp, fontWeight = FontWeight.Light, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            RoundPad(listOf(listOf("C", "⌫", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("( )", "0", ".", "="))) { press(it) }
            Spacer(Modifier.height(14.dp))
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
        CalcPad(rows, classic, Modifier.weight(1f)) { press(it) }
        Spacer(Modifier.height(8.dp))
    }
}


/** Round keys in rows, as large as the width allows (but not taller than 84 dp), sitting at the bottom: the room above is the display's. */
@Composable
private fun RoundPad(rows: List<List<String>>, onKey: (String) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val cols = rows[0].size
        val side = minOf((maxWidth - gap * (cols - 1)) / cols, 84.dp)
        Column(Modifier.align(Alignment.BottomCenter), verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) { r.forEach { k -> CalcKey(k, Modifier.size(side), false) { onKey(k) } } } }
        }
    }
}

/** A grid of keys that fills the room it is given: every row and every key takes an equal share, in the key style of the look. */
@Composable
internal fun CalcPad(rows: List<List<String>>, classic: Boolean, modifier: Modifier = Modifier, onKey: (String) -> Unit) {
    val gap = if (classic) 4.dp else if (T.id == "win10") 2.dp else if (T.id == "amoled" || T.id == "material") 8.dp else 3.dp
    val round = T.id == "amoled" || T.id == "material"
    if (!round) {
        // Windows looks: keys of a modest height, sitting at the bottom; the room above them is for the display
        androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            val n = rows.size
            val fit = if (constraints.hasBoundedHeight) (maxHeight - gap * (n - 1)) / n else 48.dp
            val rowH = minOf(fit, 58.dp)
            Column(Modifier.fillMaxWidth().widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().height(rowH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { k -> CalcKey(k, Modifier.weight(1f).fillMaxHeight(), classic) { onKey(k) } }
                    }
                }
            }
        }
        return
    }
    Column(modifier.fillMaxWidth().widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { k ->
                    // round keys stay round: a square as big as the cell allows, centred (a stretched circle is a thin pill)
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        val side = minOf(maxWidth, maxHeight)
                        CalcKey(k, Modifier.size(side), classic) { onKey(k) }
                    }
                }
            }
        }
    }
}

private fun modeTitle(m: String) = when (m) {
    "scientific" -> tr("Scientific", "Bilimsel"); "graph" -> tr("Graphing", "Grafik"); "convert" -> tr("Converter", "Dönüştürücü")
    "programmer" -> tr("Programmer", "Programcı"); else -> tr("Calculator", "Hesap makinesi")
}

/** The menu: the modes, and the look of the calculator (Windows 11 / 10 / 7 / XP / 95, Material, AMOLED). */
@Composable
private fun CalcMenu(mode: String, onMode: (String) -> Unit, onClose: () -> Unit) {
    var look by remember { mutableStateOf(false) }
    val c = com.umutk.pulse.LocalExtraLook.current
    if (look && c != null) com.umutk.pulse.SetLookDialog(tr("Look", "Görünüm"), com.umutk.pulse.ExtraToolLook.ids, c.id, { com.umutk.pulse.ExtraToolLook.name(it) }, { c.set(it) }) { look = false; onClose() }
    else com.umutk.pulse.WinAlert(onDismissRequest = onClose, title = { Text(tr("Calculator", "Hesap makinesi")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("standard" to "calc", "scientific" to "function", "graph" to "performance", "convert" to "scale", "programmer" to "terminal").forEach { (m, icon) ->
                    Row(Modifier.fillMaxWidth().clickable { onMode(m) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon(icon, if (m == mode) Look.accent else T.text, 22.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(modeTitle(m), color = if (m == mode) Look.accent else T.text, fontSize = 16.sp)
                    }
                }
                if (c != null) Row(Modifier.fillMaxWidth().clickable { look = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("palette", T.text, 22.dp); Spacer(Modifier.size(12.dp)); Text(tr("Look", "Görünüm"), color = T.text, fontSize = 16.sp)
                }
            }
        },
        confirmButton = { com.umutk.pulse.PButton(tr("Close", "Kapat"), onClick = onClose) })
}

/** One key in the look of the window: every Windows look has its own key, Material and AMOLED have round ones. */
@Composable
internal fun CalcKey(k: String, modifier: Modifier, classic: Boolean, onClick: () -> Unit) {
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
                id == "amoled" -> when { equals -> acc; op -> Color(0xFF1F1F1F); else -> Color(0xFF151515) }
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
        round && k in setOf("C", "CE", "⌫") -> Color(0xFFEF5350)
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
    ) { Text(k, color = ink, fontSize = if (mem) 14.sp else if (round) 26.sp else 20.sp, fontWeight = if (digit || id == "xp") FontWeight.SemiBold else FontWeight.Normal) }
}

private fun Color.compositeOverBg(): Color = compositeOver(T.panel)
