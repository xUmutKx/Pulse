package com.umutk.pulse.extras.athenaeum.ui

import com.umutk.pulse.tr
import com.umutk.pulse.R

import android.content.Intent
import com.umutk.pulse.extras.athenaeum.TarotApp
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val ONBOARDING_PAGES = 4
private val lastPageIndex = ONBOARDING_PAGES - 1

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }

    val saveName: () -> Unit = {
        if (name.isNotBlank()) TarotApp.core.setUserName(name.trim())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (page < lastPageIndex) {
            TextButton(
                onClick = { page = lastPageIndex },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
            ) {
                Text(tr("Skip", "Geç"))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (page) {
                    0 -> WelcomeSlide(name) { name = it }
                    1 -> TourSlide()
                    2 -> CustomizeSlide()
                    else -> PrivacySlide()
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(ONBOARDING_PAGES) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == page) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page > 0) {
                    TextButton(onClick = { page-- }) {
                        Text(tr("Back", "Geri"))
                    }
                } else {
                    Spacer(modifier = Modifier.width(72.dp))
                }
                Button(
                    onClick = {
                        if (page < lastPageIndex) {
                            page++
                        } else {
                            saveName()
                            onFinish()
                        }
                    }
                ) {
                    Text(if (page < lastPageIndex) tr("Next", "İleri") else tr("Begin", "Başla"))
                }
            }
        }
    }
}

@Composable
private fun WelcomeSlide(name: String, onNameChange: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Icon(
            painter = painterResource(R.drawable.ic_moon_first_quarter),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp)
        )
        Text(
            text = "Athenaeum",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = tr("Your companion for tarot, astrology, runes, spirits, and more.", "Tarot, astroloji, rünler, ruhlar ve daha fazlası için yol arkadaşın."),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 24.dp)
                .padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = tr("What should we call you?", "Sana ne diyelim?"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text(tr("A name, a nickname, a craft-name\u2026", "Bir ad, bir takma ad, bir sanat adı…")) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun TourSlide() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = tr("What's inside", "İçinde neler var"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(24.dp))
        TourRow(R.drawable.ic_onboarding_tarot_fool, "Tarot", tr("Draws, spreads, and every card's meanings - all editable.", "Çekilişler, açılımlar ve her kartın anlamları; hepsi düzenlenebilir."))
        TourRow(R.drawable.ic_moon_first_quarter, tr("Astrology", "Astroloji"), tr("Today's sun and moon signs, planetary days, the zodiac.", "Bugünün güneş ve ay burçları, gezegen günleri, zodyak."))
        TourRow(R.drawable.ic_onboarding_magic, tr("Magic", "Büyü"), tr("Spells, Icelandic staves, and the Elder Futhark runes.", "Büyüler, İzlanda asaları ve Elder Futhark rünleri."))
        TourRow(R.drawable.ic_skull, tr("Spirits", "Ruhlar"), tr("Entities, angels, and deities of many pantheons.", "Pek çok panteondan varlıklar, melekler ve tanrılar."))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = tr("Trim or disable anything in Settings.", "Her şeyi Ayarlar'dan kısabilir ya da kapatabilirsin."),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TourRow(iconRes: Int, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CustomizeSlide() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = tr("Make it yours", "Kendine göre yap"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(24.dp))
        Bullet(tr("Everything is editable - card meanings, rune entries, spirit profiles.", "Her şey düzenlenebilir: kart anlamları, rün girdileri, ruh profilleri."))
        Bullet(tr("Add your own spells, spirits, and deities from the + buttons.", "+ düğmeleriyle kendi büyülerini, ruhlarını ve tanrılarını ekle."))
        Bullet(tr("Don't use a section? Turn it off in Settings \u2192 Visible sections.", "Bir bölümü kullanmıyor musun? Ayarlar → Görünen bölümler'den kapat."))
        Bullet(tr("Back everything up - one file, any app or drive you like.", "Her şeyi yedekle: tek dosya, istediğin uygulama ya da sürücü."))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = tr("The built-in content is a starting point, not gospel.", "Hazır içerik bir başlangıçtır, kesin doğru değildir."),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PrivacySlide() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(24.dp))
        Icon(
            painter = painterResource(R.drawable.ic_entity_wraith),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Text(
            text = tr("Yours alone", "Yalnızca senin"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Bullet(tr("Everything stays on this device.", "Her şey bu cihazda kalır."))
        Bullet(tr("No account. No analytics. No tracking. Ever.", "Hesap yok. Analiz yok. İzleme yok. Asla."))
        Bullet(tr("For reflection and tradition - not medical, legal, or financial advice.", "Düşünmek ve gelenek içindir; tıbbi, hukuki ya da mali tavsiye değildir."))
    }
}

