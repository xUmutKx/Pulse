package com.elcesarmaat.lockart

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Google Sans Flex, la fuente del sistema de los Pixel; en otros teléfonos cae a la fuente por defecto. */
private val Flex = DeviceFontFamilyName("google-sans-flex")
private val FlexFamily = FontFamily(
    Font(Flex, FontWeight.Normal),
    Font(Flex, FontWeight.Medium),
    Font(Flex, FontWeight.SemiBold),
    Font(Flex, FontWeight.Bold),
)

/** La del reloj del bloqueo, para la vista previa. */
private val FlexClock = DeviceFontFamilyName("google-sans-flex-clock")
val ClockFamily = FontFamily(Font(FlexClock, FontWeight.Normal), Font(FlexClock, FontWeight.Medium))

private fun TextStyle.flex() = copy(fontFamily = FlexFamily)

private val FlexTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.flex(),
        displayMedium = displayMedium.flex(),
        displaySmall = displaySmall.flex(),
        headlineLarge = headlineLarge.flex(),
        headlineMedium = headlineMedium.flex(),
        headlineSmall = headlineSmall.flex(),
        titleLarge = titleLarge.flex(),
        titleMedium = titleMedium.flex(),
        titleSmall = titleSmall.flex(),
        bodyLarge = bodyLarge.flex(),
        bodyMedium = bodyMedium.flex(),
        bodySmall = bodySmall.flex(),
        labelLarge = labelLarge.flex(),
        labelMedium = labelMedium.flex(),
        labelSmall = labelSmall.flex(),
    )
}

/** Material 3 Expressive con el color dinámico del fondo de pantalla, como las apps del sistema. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockArtTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = FlexTypography,
        content = content,
    )
}
