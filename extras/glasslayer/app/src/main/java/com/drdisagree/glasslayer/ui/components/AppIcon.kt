package com.drdisagree.glasslayer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.theme.OnInk

@Composable
fun AppIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tint: Color = OnInk,
    size: Dp = 22.dp
) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
