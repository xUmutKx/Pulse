package com.umutk.pulse.extras.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.umutk.pulse.Look
import com.umutk.pulse.PIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A person of the contacts that the start menu's search found: the name and the number a tap calls. */
class ContactHit(val name: String, val number: String)

/** The people of the contacts whose name has [q] (at most six), when the setting is on and the contacts may be read; empty otherwise. Blocking. */
fun findContacts(c: android.content.Context, q: String): List<ContactHit> {
    if (!LauncherPrefs.searchContacts || q.trim().length < 2) return emptyList()
    if (ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val out = LinkedHashMap<String, ContactHit>()
    runCatching {
        c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%" + q.trim() + "%"), ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cur ->
            while (cur.moveToNext() && out.size < 6) {
                val n = cur.getString(0).orEmpty(); val num = cur.getString(1).orEmpty()
                if (n.isNotBlank() && num.isNotBlank() && n !in out) out[n] = ContactHit(n, num)
            }
        }
    }
    return out.values.toList()
}

/** Calls [number] at once when the phone permission is given, else opens the dialer with it. */
fun callNow(c: android.content.Context, number: String) {
    val can = ContextCompat.checkSelfPermission(c, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    runCatching { c.startActivity(Intent(if (can) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The contacts that match the start menu's search, each a row with its name, its number and a call button (nothing when the setting is off). */
@Composable
fun ContactResults(query: String, fg: Color, sub: Color, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val hits by produceState(emptyList<ContactHit>(), query, LauncherPrefs.searchContacts) {
        delay(150)
        value = withContext(Dispatchers.IO) { findContacts(ctx, query) }
    }
    if (hits.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        hits.forEach { h ->
            Row(Modifier.fillMaxWidth().clickable { callNow(ctx, h.number) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Look.accent.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                    Text(h.name.take(1).uppercase(), color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(h.name, color = fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(h.number, color = sub, fontSize = 12.sp, maxLines = 1)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2E7D32)).clickable { callNow(ctx, h.number) }, contentAlignment = Alignment.Center) { PIcon("phone", Color.White, 20.dp) }
            }
        }
    }
}
