package com.umutk.pulse.extras.video

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.umutk.pulse.PIcon
import com.umutk.pulse.T

private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** True when a fingerprint, face or screen lock exists to ask for. */
internal fun canLock(ctx: Context): Boolean =
    android.os.Build.VERSION.SDK_INT >= 30 && ctx.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

/** Full-screen lock: asks for the fingerprint, face or screen lock; the lock icon asks again. Back leaves the app. */
@Composable
internal fun VideoLockGate(onBack: () -> Unit, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var tries by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    LaunchedEffect(tries) {
        if (!canLock(ctx)) { onOpen(); return@LaunchedEffect } // nothing to ask: do not lock the user out
        if (android.os.Build.VERSION.SDK_INT < 30) { onOpen(); return@LaunchedEffect }
        askLock(ctx, onOpen)
    }
    Box(Modifier.fillMaxSize().background(T.bg).clickable { tries++ }, contentAlignment = Alignment.Center) { PIcon("applock", T.sub, 56.dp) }
}

@androidx.annotation.RequiresApi(30)
private fun askLock(ctx: Context, onOpen: () -> Unit) {
    val cancel = CancellationSignal()
    val prompt = BiometricPrompt.Builder(ctx).setTitle(com.umutk.pulse.tr("Video Player", "Video Oynatıcı")).setAllowedAuthenticators(AUTH).build()
    prompt.authenticate(cancel, ctx.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOpen() }
    })
}
