package com.drdisagree.glasslayer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.components.liquid.LiquidButton
import com.drdisagree.glasslayer.ui.theme.OnInk

@Composable
fun GlassIconButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    description: String? = null,
    tint: Color = Color.Unspecified,
    iconTint: Color = OnInk,
    iconModifier: Modifier = Modifier
) {
    LiquidButton(
        onClick = onClick,
        backdrop = LocalBackdrop.current,
        modifier = modifier
            .size(size)
            .semantics {
                role = Role.Button
                if (description != null) contentDescription = description
            },
        tint = tint,
        height = size,
        horizontalPadding = 0.dp
    ) {
        AppIcon(icon, modifier = iconModifier, tint = iconTint, size = size * 0.45f)
    }
}
