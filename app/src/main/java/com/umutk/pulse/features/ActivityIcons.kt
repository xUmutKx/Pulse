package com.umutk.pulse.features

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.DownhillSkiing
import androidx.compose.material.icons.outlined.EmojiPeople
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.IceSkating
import androidx.compose.material.icons.outlined.Kayaking
import androidx.compose.material.icons.outlined.Kitesurfing
import androidx.compose.material.icons.outlined.Moving
import androidx.compose.material.icons.outlined.NordicWalking
import androidx.compose.material.icons.outlined.Paragliding
import androidx.compose.material.icons.outlined.Phishing
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.RollerSkating
import androidx.compose.material.icons.outlined.Rowing
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Skateboarding
import androidx.compose.material.icons.outlined.Sledding
import androidx.compose.material.icons.outlined.Snowboarding
import androidx.compose.material.icons.outlined.Sports
import androidx.compose.material.icons.outlined.SportsBaseball
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material.icons.outlined.SportsGolf
import androidx.compose.material.icons.outlined.SportsGymnastics
import androidx.compose.material.icons.outlined.SportsHandball
import androidx.compose.material.icons.outlined.SportsHockey
import androidx.compose.material.icons.outlined.SportsKabaddi
import androidx.compose.material.icons.outlined.SportsMartialArts
import androidx.compose.material.icons.outlined.SportsMma
import androidx.compose.material.icons.outlined.SportsRugby
import androidx.compose.material.icons.outlined.SportsScore
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.SportsTennis
import androidx.compose.material.icons.outlined.SportsVolleyball
import androidx.compose.material.icons.outlined.Stairs
import androidx.compose.material.icons.outlined.Surfing
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.TrackChanges
import com.umutk.pulse.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umutk.pulse.T

/** Outlined Material vector for an activity key; also the stand-in on the Windows looks (no sport glyphs in the Fluent vector set). */
fun activityVector(key: String): ImageVector = when (key) {
    "walking" -> Icons.Outlined.DirectionsWalk
    "running" -> Icons.Outlined.DirectionsRun
    "hiking" -> Icons.Outlined.Hiking
    "cycling" -> Icons.Outlined.DirectionsBike
    "swimming", "scuba_diving" -> Icons.Outlined.Pool
    "yoga" -> Icons.Outlined.SelfImprovement
    "pilates" -> Icons.Outlined.Accessibility
    "gym" -> Icons.Outlined.FitnessCenter
    "rowing" -> Icons.Outlined.Rowing
    "elliptical", "scooter" -> Icons.Outlined.Moving
    "stairs" -> Icons.Outlined.Stairs
    "dancing" -> Icons.Outlined.EmojiPeople
    "football" -> Icons.Outlined.SportsSoccer
    "basketball" -> Icons.Outlined.SportsBasketball
    "tennis", "squash" -> Icons.Outlined.SportsTennis
    "volleyball" -> Icons.Outlined.SportsVolleyball
    "golf" -> Icons.Outlined.SportsGolf
    "skiing" -> Icons.Outlined.DownhillSkiing
    "snowboarding" -> Icons.Outlined.Snowboarding
    "ice_skating" -> Icons.Outlined.IceSkating
    "curling" -> Icons.Outlined.SportsScore
    "orienteering" -> Icons.Outlined.Explore
    "climbing" -> Icons.Outlined.Terrain
    "boxing" -> Icons.Outlined.SportsMma
    "martial_arts" -> Icons.Outlined.SportsMartialArts
    "jump_rope", "aerobics" -> Icons.Outlined.SportsGymnastics
    "horse_riding" -> Icons.Outlined.SportsKabaddi
    "kayaking" -> Icons.Outlined.Kayaking
    "sailing" -> Icons.Outlined.Sailing
    "surfing" -> Icons.Outlined.Surfing
    "kitesurfing" -> Icons.Outlined.Kitesurfing
    "archery" -> Icons.Outlined.TrackChanges
    "fishing" -> Icons.Outlined.Phishing
    "nordic_walking" -> Icons.Outlined.NordicWalking
    "roller_skating" -> Icons.Outlined.RollerSkating
    "skateboarding" -> Icons.Outlined.Skateboarding
    "hockey" -> Icons.Outlined.SportsHockey
    "baseball" -> Icons.Outlined.SportsBaseball
    "rugby" -> Icons.Outlined.SportsRugby
    "cricket" -> Icons.Outlined.SportsCricket
    "handball" -> Icons.Outlined.SportsHandball
    "paragliding" -> Icons.Outlined.Paragliding
    "sledding" -> Icons.Outlined.Sledding
    else -> Icons.Outlined.Sports // badminton, table_tennis, other
}

/** Windows 11 uses the Fluency PNG (assets/icons/win11/act_<key>.png) when there is one; every other look uses the outlined vector, tinted. */
@Composable
fun ActivityIcon(key: String, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    if (T.id == "win11" && !T.material) {
        com.umutk.pulse.features.ThemeIcons.bitmap(ctx, "win11", "act_$key")?.let { Image(it, null, modifier.size(size)); return }
    }
    Icon(activityVector(key), null, modifier.size(size), tint)
}
