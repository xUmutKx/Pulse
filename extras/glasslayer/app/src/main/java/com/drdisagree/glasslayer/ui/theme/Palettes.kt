package com.drdisagree.glasslayer.ui.theme

import androidx.compose.ui.graphics.Color

val LightPalette = Palette(
    isDark = false,
    ink = Color(0xFFF2F2F7),
    onInk = Color(0xFF000000),
    onInkMuted = Color(0x993C3C43),
    onInkFaint = Color(0x4D3C3C43),
    container = Color.White.copy(alpha = 0.5f),
    hairline = Color(0x1F000000),
    accent = Color(0xFF0088FF),
    toggleTrack = Color(0xFF787878).copy(alpha = 0.2f),
    toggleOn = Color(0xFF34C759),
    mesh = listOf(Color(0xFFF5F6F8), Color(0xFFCFE8EC), Color(0xFFD6E2F3), Color(0xFFD2EEE0))
)

val DarkPalette = Palette(
    isDark = true,
    ink = Color(0xFF000000),
    onInk = Color(0xFFF2F2F7),
    onInkMuted = Color(0x99EBEBF5),
    onInkFaint = Color(0x4DEBEBF5),
    container = Color.White.copy(alpha = 0.1f),
    hairline = Color(0x1FFFFFFF),
    accent = Color(0xFF0091FF),
    toggleTrack = Color(0xFF787880).copy(alpha = 0.36f),
    toggleOn = Color(0xFF30D158),
    mesh = listOf(Color(0xFF0B0D10), Color(0xFF0F3B44), Color(0xFF12304F), Color(0xFF0E3A2C))
)
