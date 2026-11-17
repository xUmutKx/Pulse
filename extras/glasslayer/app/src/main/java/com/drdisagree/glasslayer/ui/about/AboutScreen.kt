package com.drdisagree.glasslayer.ui.about

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.BuildConfig
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.ui.components.AndroidGlassScene
import com.drdisagree.glasslayer.ui.components.AppIcon
import com.drdisagree.glasslayer.ui.components.GlassContainer
import com.drdisagree.glasslayer.ui.components.PageScaffold
import com.drdisagree.glasslayer.ui.components.openUrl
import com.drdisagree.glasslayer.ui.components.pressable
import com.drdisagree.glasslayer.ui.settings.RowDivider
import com.drdisagree.glasslayer.ui.settings.SectionLabel
import com.drdisagree.glasslayer.ui.theme.Accent
import com.drdisagree.glasslayer.ui.theme.GlassLayerTheme
import com.drdisagree.glasslayer.ui.theme.Hairline
import com.drdisagree.glasslayer.ui.theme.OnInk
import com.drdisagree.glasslayer.ui.theme.OnInkFaint
import com.drdisagree.glasslayer.ui.theme.OnInkMuted
import com.kyant.shapes.Capsule

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    val developerUrl = stringResource(R.string.developer_url)
    val sourceUrl = stringResource(R.string.source_url)
    val supportUrl = stringResource(R.string.support_url)
    PageScaffold(stringResource(R.string.about_title), onBack) {
        item(key = "identity") { Identity() }
        item(key = "links_label") { SectionLabel(stringResource(R.string.about_links), icon = R.drawable.ic_section_links) }
        item(key = "links") {
            GlassContainer(CardShape, Modifier.fillMaxWidth()) {
                LinkRow(R.drawable.ic_code, stringResource(R.string.about_source), stringResource(R.string.about_source_hint)) {
                    openUrl(context, sourceUrl)
                }
                RowDivider()
                LinkRow(R.drawable.ic_coffee, stringResource(R.string.about_support), stringResource(R.string.about_support_hint)) {
                    openUrl(context, supportUrl)
                }
                RowDivider()
                LinkRow(R.drawable.ic_person, stringResource(R.string.about_developer), stringResource(R.string.about_developer_name)) {
                    openUrl(context, developerUrl)
                }
                RowDivider()
                LinkRow(R.drawable.ic_document, stringResource(R.string.licenses), stringResource(R.string.about_licenses_hint), onOpenLicenses)
            }
        }
        item(key = "footer") {
            Text(
                stringResource(R.string.about_footer),
                style = MaterialTheme.typography.labelMedium,
                color = OnInkFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun Identity() {
    GlassContainer(CardShape, Modifier.fillMaxWidth()) {
        AndroidGlassScene(
            Modifier
                .fillMaxWidth()
                .height(SCENE_HEIGHT)
                .padding(8.dp)
                .clip(InnerShape)
                .background(OnInk.copy(alpha = INNER_FILL))
                .border(1.dp, Hairline, InnerShape)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = OnInk
            )
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = OnInkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = Accent,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(Capsule())
                    .background(Accent.copy(alpha = VERSION_FILL))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun LinkRow(@DrawableRes icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(Role.Button, onClick)
            .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = ICON_FILL)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(icon, tint = Accent, size = 20.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = OnInk, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnInkMuted, maxLines = 1)
        }
        AppIcon(R.drawable.ic_chevron, tint = OnInkMuted, size = 18.dp)
    }
}

@Preview(showSystemUi = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun AboutPreview() {
    GlassLayerTheme(dark = true) {
        AboutScreen(onBack = {}, onOpenLicenses = {})
    }
}

private val CardShape = RoundedCornerShape(28.dp)
private val InnerShape = RoundedCornerShape(22.dp)
private val SCENE_HEIGHT = 180.dp
private const val VERSION_FILL = 0.16f
private const val INNER_FILL = 0.05f
private const val ICON_FILL = 0.16f
