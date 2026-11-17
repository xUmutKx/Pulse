package com.drdisagree.glasslayer.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.theme.OnInk

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineLarge,
        color = OnInk,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}
