package com.umutk.pulse.extras.palette.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.palette.core.color.relativeLuminance
import com.umutk.pulse.extras.palette.core.icon.IconEngine
import com.umutk.pulse.extras.palette.core.icon.IconOverride
import com.umutk.pulse.extras.palette.core.icon.LauncherSkin
import com.umutk.pulse.extras.palette.core.icon.ThemeSpec
import com.umutk.pulse.extras.palette.AppInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Realistic home-screen simulator.
 *
 * Not a mockup image: it draws the *actual* generated icons, so the preview and
 * the exported pack can never disagree. Skins change grid density, label style,
 * clock typography, dock treatment and corner language — the four launcher
 * families people actually theme.
 */
@Composable
fun LauncherPreview(
    spec: ThemeSpec,
    apps: List<AppInfo>,
    engine: IconEngine,
    wallpaper: Bitmap? = null,
    overrides: Map<String, IconOverride> = emptyMap(),
    modifier: Modifier = Modifier,
    skin: LauncherSkin = spec.skin
) {
    val palette = spec.palette
    val bgColor = Color(palette.sample(0.35f))
    val onWall = if (palette.sample(0.35f).relativeLuminance() > 0.45f) Color(0xFF14121A) else Color.White
    val cols = when (skin) {
        LauncherSkin.SAMSUNG -> 4
        LauncherSkin.NOTHING -> 4
        LauncherSkin.PIXEL -> 4
        LauncherSkin.STOCK -> 4
    }
    val iconSize = 58.dp

    Box(
        modifier
            .clip(RoundedCornerShape(34.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(palette.sample(0.55f)),
                        Color(palette.sample(0.25f))
                    )
                )
            )
    ) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(bgColor.copy(alpha = 0.28f))
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            StatusBar(onWall, skin)

            // hero widget
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(if (skin == LauncherSkin.NOTHING) 8.dp else 26.dp))
                    .background(Color(palette.sample(0.75f)).copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f),
                        RoundedCornerShape(if (skin == LauncherSkin.NOTHING) 8.dp else 26.dp))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                        color = onWall,
                        fontSize = if (skin == LauncherSkin.PIXEL) 34.sp else 30.sp,
                        fontWeight = if (skin == LauncherSkin.NOTHING) FontWeight.Light else FontWeight.SemiBold
                    )
                    Text(
                        SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date()),
                        color = onWall.copy(alpha = 0.75f), fontSize = 12.sp
                    )
                }
            }

            // grid
            val gridApps = apps.take(cols * 3)
            Column(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy((10 * spec.tileSpacing).dp)
            ) {
                gridApps.chunked(cols).forEachIndexed { rowIdx, row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEachIndexed { i, app ->
                            val index = rowIdx * cols + i
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Box {
                                    ThemedIconWithLabel(
                                        app = app, spec = spec, engine = engine, index = index,
                                        size = iconSize,
                                        labelColor = onWall,
                                        showLabel = spec.labelVisible && skin != LauncherSkin.NOTHING,
                                        override = overrides[app.key],
                                        backdrop = wallpaper
                                    )
                                    if (index == 5) Badge(13)
                                }
                            }
                        }
                        repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }

            // folder + widget row
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FolderTile(spec, apps, engine, Modifier.weight(1f))
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(palette.sample(0.85f)).copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("101", color = onWall, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(Modifier.weight(1f))

            PageDots(onWall)

            // dock
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(if (skin == LauncherSkin.SAMSUNG) 30.dp else 24.dp))
                    .background(
                        if (skin == LauncherSkin.SAMSUNG || skin == LauncherSkin.STOCK)
                            Color.White.copy(alpha = 0.12f) else Color.Transparent
                    )
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                apps.drop(cols * 3).take(4).forEachIndexed { i, app ->
                    ThemedIcon(app, spec, engine, 100 + i, iconSize,
                        overrides[app.key], wallpaper)
                }
            }

            if (skin == LauncherSkin.PIXEL || skin == LauncherSkin.STOCK) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("  Search", color = onWall.copy(alpha = 0.8f), fontSize = 13.sp,
                        modifier = Modifier.padding(start = 14.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusBar(tint: Color, skin: LauncherSkin) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            color = tint, fontSize = 12.sp,
            fontWeight = if (skin == LauncherSkin.SAMSUNG) FontWeight.Bold else FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 12.dp, height = 9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tint.copy(alpha = 0.75f))
                )
            }
        }
    }
}

@Composable
private fun Badge(count: Int) {
    Box(
        Modifier
            .padding(start = 34.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF4B3A)),
        contentAlignment = Alignment.Center
    ) {
        Text("$count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PageDots(tint: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (i == 1) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (i == 1) 0.95f else 0.45f))
            )
        }
    }
}

@Composable
private fun FolderTile(
    spec: ThemeSpec, apps: List<AppInfo>, engine: IconEngine, modifier: Modifier
) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(spec.palette.sample(0.65f)).copy(alpha = 0.42f))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            apps.take(4).chunked(2).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { i, a ->
                        ThemedIcon(a, spec, engine, 200 + r * 2 + i, 26.dp)
                    }
                }
            }
        }
    }
}
