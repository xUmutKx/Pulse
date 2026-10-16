package com.umutk.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Looks that have their own sign-in screen. */
val loginLooks = setOf("win95", "xp", "win7", "win10", "win11")

/** The look's sign-in screen, shown at start when Opt.signIn is on. Other looks just unlock. */
@Composable
fun LoginScreen(look: PTheme, onUnlock: () -> Unit) {
    when (look.id) {
        "xp" -> XpLogon(onUnlock)
        "win95" -> Win95Logon(look, onUnlock)
        "win7" -> Win7Logon(onUnlock)
        "win10", "win11" -> Win10Logon(look.id == "win11", onUnlock)
        else -> LaunchedEffect(Unit) { onUnlock() }
    }
}

/** The plain user figure used on the logon user pictures. */
@Composable
private fun UserFigure(size: androidx.compose.ui.unit.Dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        drawCircle(color, radius = this.size.width * .22f, center = Offset(this.size.width / 2, this.size.height * .3f))
        drawOval(color, Offset(this.size.width * .12f, this.size.height * .58f), Size(this.size.width * .76f, this.size.height * .6f))
    }
}

@Composable
private fun Win7Logon(onUnlock: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(painterResource(com.umutk.pulse.R.drawable.splash_win7_wallpaper), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // the glass frame around the user picture
            Box(Modifier.size(136.dp).clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x66FFFFFF))))
                .border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(8.dp)).padding(8.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF7FB6E8), Color(0xFF2C6FB3)))), contentAlignment = Alignment.Center) {
                    ProfileMark(80.dp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Pulse", color = Color.White, fontSize = 26.sp)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(200.dp).clip(RoundedCornerShape(3.dp)).background(Color.White)
                    .border(1.dp, Color(0xFF6E8AA8), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 5.dp)) {
                    if (pass.isEmpty()) Text(tr("Password", "Parola"), color = Color(0xFF8A8A8A), fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    BasicTextField(pass, { pass = it }, singleLine = true, textStyle = TextStyle(color = Color.Black, fontSize = 13.sp),
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(6.dp))
                // the round blue arrow button next to the password box
                Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF6FB3F0), Color(0xFF1C5FB0))))
                    .border(1.dp, Color(0xCCFFFFFF), androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onUnlock), contentAlignment = Alignment.Center) {
                    Text("➜", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Win10Logon(win11: Boolean, onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (win11) Image(painterResource(com.umutk.pulse.R.drawable.splash_win11_wallpaper), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1E3A), Color(0xFF1F5FA8)))))
        // the sign-in screen blurs and dims the lock-screen picture
        Box(Modifier.fillMaxSize().background(Color(0x55000000)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(150.dp, Themes.first { it.id == "win10" })
            Spacer(Modifier.height(18.dp))
            Text("Pulse", color = Color.White, fontSize = 30.sp)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.width(150.dp).clip(RoundedCornerShape(if (win11) 4.dp else 0.dp)).background(Color(0x40FFFFFF))
                .border(2.dp, Color(0x66FFFFFF), RoundedCornerShape(if (win11) 4.dp else 0.dp))
                .clickable(onClick = onUnlock).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                Text(tr("Sign in", "Oturum aç"), color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun XpLogon(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(painterResource(com.umutk.pulse.R.drawable.xp_logon_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.clickable(onClick = onUnlock).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("To begin, click your user name", "Başlamak için kullanıcı adınıza tıklayın"), color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // the user tile: a blue square with a white figure, as the XP logon shows it
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2B6FD6))
                    .border(2.dp, Color.White, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    ProfileMark(34.dp)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Pulse", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    // the green Log On button of the XP logon screen
                    Box(Modifier.clip(RoundedCornerShape(4.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF6CC45A), Color(0xFF3A9A2E))))
                        .clickable(onClick = onUnlock).padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Text(tr("Log On", "Oturum aç"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Win95Logon(look: PTheme, onUnlock: () -> Unit) {
    var user by remember { mutableStateOf("Pulse User") }
    var pass by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color(0xFF008080)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(330.dp).background(look.bg).bevel(true).padding(3.dp)) {
            // the title bar with a grey close button
            Row(Modifier.fillMaxWidth().height(22.dp)
                .then(look.title?.let { Modifier.background(it) } ?: Modifier.background(look.accent))
                .padding(start = 4.dp, end = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Welcome to Windows", "Windows'a hoş geldiniz"), color = look.titleText, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(16.dp).background(look.bg).bevel(true), contentAlignment = Alignment.Center) {
                    Text("?", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(2.dp))
                Box(Modifier.size(16.dp).background(look.bg).bevel(true), contentAlignment = Alignment.Center) {
                    Text("✕", color = Color.Black, fontSize = 9.sp)
                }
            }
            Row(Modifier.padding(12.dp)) {
                // the key icon of the 95 logon, drawn with the Windows colours
                Canvas(Modifier.size(36.dp)) {
                    val u = size.width / 36f
                    drawCircle(Color(0xFFFFD800), radius = 9f * u, center = Offset(10f * u, 18f * u))
                    drawCircle(look.bg, radius = 3f * u, center = Offset(10f * u, 18f * u))
                    drawRect(Color(0xFFFFD800), Offset(18f * u, 16f * u), Size(16f * u, 4f * u))
                    drawRect(Color(0xFFFFD800), Offset(28f * u, 20f * u), Size(3f * u, 5f * u))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Type a user name and password to log on to Windows.", "Windows'a giriş yapmak için kullanıcı adı ve parola yazın."),
                        color = look.text, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("User name:", "Kullanıcı adı:"), color = look.text, fontSize = 12.sp, modifier = Modifier.width(72.dp))
                        Win95Field(user, { user = it }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Password:", "Parola:"), color = look.text, fontSize = 12.sp, modifier = Modifier.width(72.dp))
                        Win95Field(pass, { pass = it }, Modifier.weight(1f), hidden = true)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Win95Button("OK", onUnlock)
                    Win95Button(tr("Cancel", "İptal")) { }
                }
            }
        }
    }
}

/** A white sunken text box, as in the 95 dialogs. */
@Composable
private fun Win95Field(value: String, onChange: (String) -> Unit, modifier: Modifier, hidden: Boolean = false) {
    BasicTextField(value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = Color.Black, fontSize = 13.sp), cursorBrush = SolidColor(Color.Black),
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.background(Color.White).bevel(false).padding(3.dp))
}

/** A grey raised 95 button. */
@Composable
private fun Win95Button(text: String, onClick: () -> Unit) {
    Box(Modifier.width(75.dp).height(23.dp).background(Color(0xFFC0C0C0)).bevel(true).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(text, color = Color.Black, fontSize = 12.sp)
    }
}
