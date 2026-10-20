package com.umutk.pulse.extras.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PDialog
import com.umutk.pulse.PField
import com.umutk.pulse.PRadio
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr

/** A list of options with one radio marked; a pick closes the dialog through [onPick]. */
@Composable
fun NPick(title: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(title, color = T.text) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(key) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PRadio(selected == key) { onPick(key) }
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = T.text, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { PButton(tr("Close", "Kapat"), compact = true) { onDismiss() } },
    )
}

/** A text box with optional quick picks (for example the folders already in use). */
@Composable
fun NInput(title: String, hint: String, initial: String, quick: List<String>, onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(title, color = T.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PField(value, { value = it }, hint, Modifier.fillMaxWidth())
                quick.forEach { q -> Text(q, color = T.accent, fontSize = 14.sp, modifier = Modifier.clickable { onOk(q) }.padding(vertical = 4.dp)) }
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam"), compact = true) { onOk(value.trim()) } },
        dismissButton = { PButton(tr("Cancel", "İptal"), compact = true) { onDismiss() } },
    )
}

/** A PIN box. [onSubmit] returns an error text, or null when the PIN is accepted. */
@Composable
fun NPin(title: String, onSubmit: (String) -> String?, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(title, color = T.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().border(1.dp, T.line, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    BasicTextField(
                        pin, { v -> pin = v.filter { it.isDigit() }.take(8) },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = T.text, fontSize = 18.sp),
                        cursorBrush = SolidColor(T.accent),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        decorationBox = { inner -> Box { if (pin.isEmpty()) Text(tr("PIN (4 to 8 digits)", "PIN (4 ila 8 hane)"), color = T.sub, fontSize = 16.sp); inner() } },
                    )
                }
                error?.let { Text(it, color = Color(0xFFE53935), fontSize = 13.sp) }
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam"), compact = true) { if (pin.length >= 4) error = onSubmit(pin) else error = tr("At least 4 digits", "En az 4 hane") } },
        dismissButton = { PButton(tr("Cancel", "İptal"), compact = true) { onDismiss() } },
    )
}

/** Seven swatches: none and the label colours. */
@Composable
fun NSwatch(title: String, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(title, color = T.text) },
        text = { NSwatchRow(selected, onPick) },
        confirmButton = { PButton(tr("Close", "Kapat"), compact = true) { onDismiss() } },
    )
}

/** The colour swatches in one row; used by the picker dialog and the editor. */
@Composable
fun NSwatchRow(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NotesPrefs.colors.forEach { v ->
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(if (v == 0) T.panel else Color(v))
                    .border(if (v == selected) 3.dp else 1.dp, T.text, CircleShape)
                    .clickable { onPick(v) },
            )
        }
    }
}

/** A locked note's place: nothing of the note shows until the PIN is right. */
@Composable
fun NLockedView(onUnlock: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    NPin(tr("Enter PIN", "PIN girin"), { pin -> if (NotesPrefs.checkPin(ctx, pin)) { onUnlock(); null } else tr("Wrong PIN", "Yanlış PIN") }, onBack)
}
