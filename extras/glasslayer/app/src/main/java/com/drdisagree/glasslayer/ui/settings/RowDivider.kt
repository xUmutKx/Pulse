package com.drdisagree.glasslayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.theme.Hairline

@Composable
internal fun RowDivider() {
    Box(
        Modifier
            .padding(start = 20.dp, end = 20.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline)
    )
}
