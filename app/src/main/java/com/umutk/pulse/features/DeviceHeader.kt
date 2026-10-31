package com.umutk.pulse.features

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/** The top of the Overview page: a flat picture of the phone, and its name under it. */
@Composable
fun DeviceHeader() {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.width(92.dp).height(150.dp)) {
            val w = size.width
            val h = size.height
            val corner = CornerRadius(w * .16f, w * .16f)
            // the body, a dark frame, the screen, and the camera at the top left
            drawRoundRect(color = T.text.copy(alpha = .85f), size = size, cornerRadius = corner)
            drawRoundRect(color = Color.Black, topLeft = Offset(w * .05f, h * .04f), size = Size(w * .9f, h * .92f), cornerRadius = CornerRadius(w * .12f, w * .12f))
            drawRoundRect(color = T.text.copy(alpha = .18f), topLeft = Offset(w * .09f, h * .08f), size = Size(w * .82f, h * .84f), cornerRadius = CornerRadius(w * .08f, w * .08f))
            drawCircle(color = T.text.copy(alpha = .9f), radius = w * .05f, center = Offset(w * .22f, h * .16f))
        }
        Text(phoneName(), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
    }
}
