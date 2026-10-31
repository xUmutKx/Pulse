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
            Canvas(Modifier.width(96.dp).height(168.dp)) {
                val w = size.width
                val h = size.height
                // the body: a flat frame with the rounded corners of the phone
                drawRoundRect(color = Color(0xFF3A3A3C), size = size, cornerRadius = CornerRadius(w * .14f, w * .14f))
                drawRoundRect(color = Color(0xFF8E8E93), topLeft = Offset(w * .03f, h * .03f), size = Size(w * .94f, h * .94f), cornerRadius = CornerRadius(w * .12f, w * .12f))
                // the screen, with a thin black bezel
                drawRoundRect(color = Color.Black, topLeft = Offset(w * .06f, h * .055f), size = Size(w * .88f, h * .89f), cornerRadius = CornerRadius(w * .1f, w * .1f))
                drawRoundRect(color = Color(0xFF16181C), topLeft = Offset(w * .085f, h * .08f), size = Size(w * .83f, h * .84f), cornerRadius = CornerRadius(w * .07f, w * .07f))
                // the camera block at the top left: a square with three lenses and the flash
                val cam = Size(w * .42f, h * .2f)
                drawRoundRect(color = Color(0xFF0B0B0D), topLeft = Offset(w * .13f, h * .1f), size = cam, cornerRadius = CornerRadius(w * .07f, w * .07f))
                val lens = w * .065f
                val lx = w * .13f + cam.width * .3f
                val ly = h * .1f + cam.height * .36f
                drawCircle(color = Color(0xFF2B2F36), radius = lens, center = Offset(lx, ly))
                drawCircle(color = Color(0xFF2B2F36), radius = lens, center = Offset(lx + cam.width * .4f, ly))
                drawCircle(color = Color(0xFF2B2F36), radius = lens * .8f, center = Offset(lx + cam.width * .2f, ly + cam.height * .42f))
                drawCircle(color = Color(0xFFE8E2C8), radius = w * .02f, center = Offset(w * .13f + cam.width * .8f, h * .1f + cam.height * .24f))
            }
        }
        Text(phoneName(), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
    }
}
