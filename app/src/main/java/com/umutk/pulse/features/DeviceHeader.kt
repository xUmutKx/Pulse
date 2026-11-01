package com.umutk.pulse.features

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.umutk.pulse.T

/** The series name of the Galaxy phones by their model number (SM-S911B is the S23); other phones show their own model. */
private val GALAXY = mapOf(
    "S901" to "S22", "S906" to "S22+", "S908" to "S22 Ultra", "S911" to "S23", "S916" to "S23+", "S918" to "S23 Ultra", "S711" to "S23 FE",
    "S921" to "S24", "S926" to "S24+", "S928" to "S24 Ultra", "S931" to "S25", "S936" to "S25+", "S938" to "S25 Ultra",
)

/** The name under the phone picture: "S23 (S911B)" for a Galaxy, else the maker and the model. */
fun phoneName(): String {
    val model = Build.MODEL.orEmpty()
    val code = model.removePrefix("SM-")
    val series = GALAXY.entries.firstOrNull { code.startsWith(it.key) }?.value
    return if (series != null) "$series ($code)" else listOf(Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }, model).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * The top of the Overview page: a picture of the phone and its name under it. A photo of the phone, saved as
 * assets/device_photo.png (the owner's own picture of the phone), is shown as it is; without one, a drawing of
 * the phone's shape and its camera block is drawn.
 */
@Composable
fun DeviceHeader() {
    val ctx = LocalContext.current
    val photo = remember {
        runCatching { ctx.assets.open("device_photo.png").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (photo != null) {
            Image(photo, null, Modifier.height(170.dp))
        } else {
            // a Samsung Galaxy S23 seen from the back: a thin flat aluminium frame, a glass back, the camera island at the top left
            Canvas(Modifier.width(84.dp).height(176.dp)) {
                val w = size.width
                val h = size.height
                // the aluminium frame: straight sides and small corners
                drawRoundRect(color = Color(0xFF9EA3AD), size = size, cornerRadius = CornerRadius(w * .09f, w * .09f))
                // the glass back, inside a thin edge
                drawRoundRect(color = Color(0xFF1C1E24), topLeft = Offset(w * .035f, h * .03f), size = Size(w * .93f, h * .94f), cornerRadius = CornerRadius(w * .075f, w * .075f))
                // the camera island: a square at the top left with a fine edge
                val isl = Size(w * .46f, w * .46f)
                val islTL = Offset(w * .1f, h * .075f)
                drawRoundRect(color = Color(0xFF2A2D35), topLeft = Offset(islTL.x - 1.5f, islTL.y - 1.5f), size = Size(isl.width + 3f, isl.height + 3f), cornerRadius = CornerRadius(w * .1f, w * .1f))
                drawRoundRect(color = Color(0xFF101216), topLeft = islTL, size = isl, cornerRadius = CornerRadius(w * .085f, w * .085f))
                // three lenses in the island, the flash beside them
                val lens = w * .085f
                drawCircle(color = Color(0xFF30343D), radius = lens, center = Offset(islTL.x + isl.width * .3f, islTL.y + isl.height * .3f))
                drawCircle(color = Color(0xFF0A0B0E), radius = lens * .62f, center = Offset(islTL.x + isl.width * .3f, islTL.y + isl.height * .3f))
                drawCircle(color = Color(0xFF30343D), radius = lens * .8f, center = Offset(islTL.x + isl.width * .72f, islTL.y + isl.height * .3f))
                drawCircle(color = Color(0xFF30343D), radius = lens * .8f, center = Offset(islTL.x + isl.width * .3f, islTL.y + isl.height * .72f))
                drawCircle(color = Color(0xFFE9E3CC), radius = w * .03f, center = Offset(islTL.x + isl.width * .74f, islTL.y + isl.height * .74f))
            }
        }
        Text(phoneName(), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
    }
}
