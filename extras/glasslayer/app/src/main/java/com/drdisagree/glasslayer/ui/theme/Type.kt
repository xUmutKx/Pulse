package com.drdisagree.glasslayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.drdisagree.glasslayer.R

private fun flex(weight: Int, width: Float = 100f, opticalSize: Float = 16f): FontFamily = FontFamily(
    Font(
        R.font.roboto_flex,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(width),
            FontVariation.Setting("opsz", opticalSize)
        )
    )
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = flex(700, 100f, 72f), fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = flex(700, 100f, 36f), fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = flex(600), fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = flex(450), fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = flex(600), fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = flex(400), fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = flex(400), fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = flex(400), fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = flex(560), fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = flex(500), fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = flex(500), fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
)
