package com.umutk.pulse.features

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.T

enum class ActivityType(val label: String, val icon: ImageVector) {
    Walking("Walking", Icons.Filled.DirectionsWalk),
    Running("Running", Icons.Filled.DirectionsRun),
    Cycling("Cycling", Icons.Filled.DirectionsBike),
    Swimming("Swimming", Icons.Filled.Pool),
    Football("Football", Icons.Filled.SportsSoccer),
    Hiking("Hiking", Icons.Filled.Hiking),
    Gym("Gym", Icons.Filled.FitnessCenter),
    Yoga("Yoga", Icons.Filled.SelfImprovement);

    companion object {
        fun fromName(name: String?): ActivityType = values().firstOrNull { it.name == name } ?: Walking
    }
}

/** A horizontal row of activity tiles; the chosen one is outlined in the accent colour. */
@Composable
fun ActivityPicker(selected: ActivityType, enabled: Boolean, onSelect: (ActivityType) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ActivityType.values().forEach { a ->
            val on = a == selected
            Column(
                Modifier
                    .width(72.dp)
                    .clip(shape)
                    .border(1.dp, if (on) Look.accent else T.sub.copy(alpha = 0.3f), shape)
                    .clickable(enabled = enabled) { onSelect(a) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(a.icon, a.label, Modifier.size(24.dp), tint = if (on) Look.accent else T.text)
                Spacer(Modifier.height(4.dp))
                Text(a.label, color = if (on) Look.accent else T.sub, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
