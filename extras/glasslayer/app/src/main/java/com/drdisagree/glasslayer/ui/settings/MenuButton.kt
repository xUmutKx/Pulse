package com.drdisagree.glasslayer.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.ui.components.GlassIconButton

@Composable
internal fun MenuButton(issue: Color?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(if (issue != null) 1f else 0f, spring(BADGE_DAMPING, BADGE_STIFFNESS), label = "issueBadge")
    val lastIssue = remember { BadgeColor() }
    if (issue != null) lastIssue.color = issue
    Box(modifier) {
        GlassIconButton(R.drawable.ic_more, onClick, description = stringResource(R.string.action_more))
        if (shown > 0f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(BADGE_SIZE)
                    .graphicsLayer {
                        scaleX = shown
                        scaleY = shown
                        alpha = shown.coerceIn(0f, 1f)
                    }
                    .background(lastIssue.color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("!", color = if (lastIssue.color.luminance() > LIGHT_BADGE) DARK_MARK else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private val BADGE_SIZE = 16.dp
private const val BADGE_DAMPING = 0.5f
private const val BADGE_STIFFNESS = 500f
private const val LIGHT_BADGE = 0.5f
private val DARK_MARK = Color(0xE6000000)
