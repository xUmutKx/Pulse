package com.umutk.pulse.extras.lyrics

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The question behind a long press on a player's lyrics button: delete the lyrics saved for this song. */
@Composable
fun ForgetLyricsDialog(path: String?, title: String, artist: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    com.umutk.pulse.WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(tr("Delete these lyrics?", "Bu sözler silinsin mi?")) },
        text = { Text(tr("The saved lyrics of this song (and the .lrc / .txt next to the file) are removed.", "Bu şarkının kayıtlı sözleri (ve dosyanın yanındaki .lrc / .txt) silinir.")) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                scope.launch {
                    withContext(Dispatchers.IO) { Lyrics.forget(ctx, path, title, artist) }
                    Toast.makeText(ctx, tr("Lyrics deleted", "Sözler silindi"), Toast.LENGTH_SHORT).show()
                }
            }) { Text(tr("Delete", "Sil")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Vazgeç")) } },
    )
}
