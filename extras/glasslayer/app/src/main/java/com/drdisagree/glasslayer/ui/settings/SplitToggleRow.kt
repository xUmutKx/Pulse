package com.drdisagree.glasslayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.components.LocalBackdrop
import com.drdisagree.glasslayer.ui.components.liquid.LiquidToggle
import com.drdisagree.glasslayer.ui.components.pressable
import com.drdisagree.glasslayer.ui.theme.Hairline

@Composable
internal fun SplitToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(
            title,
            summary,
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pressable(Role.Button, onOpen)
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
        )
        Box(
            Modifier
                .width(1.dp)
                .height(32.dp)
                .background(Hairline)
        )
        Box(
            Modifier
                .fillMaxHeight()
                .pressable(Role.Switch) { onCheckedChange(!checked) }
                .padding(start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidToggle(checked, onCheckedChange, LocalBackdrop.current)
        }
    }
}
