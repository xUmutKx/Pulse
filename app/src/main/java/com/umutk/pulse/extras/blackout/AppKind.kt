package com.umutk.pulse.extras.blackout

import android.content.Context
import java.util.zip.ZipFile

/** How an app draws its screen decides which BlackOut method can reach it. */
class AppKind(val title: String, val advice: String)

object AppKindScan {
    /** Looks inside the APK (and its splits, where the native libraries live) for the drawing engine the app brings with it. */
    fun detect(ctx: Context, pkg: String): AppKind? = try {
        val info = ctx.packageManager.getApplicationInfo(pkg, 0)
        val files = listOf(info.sourceDir) + (info.splitSourceDirs?.toList() ?: emptyList())
        val names = HashSet<String>()
        for (f in files) try { ZipFile(f).use { z -> for (e in z.entries()) names.add(e.name) } } catch (_: Throwable) { }
        fun has(s: String) = names.any { it.contains(s) }
        when {
            has("libflutter.so") || has("flutter_assets") -> AppKind("Flutter app",
                "It paints every pixel itself. Color rules and overlays cannot change it, whatever the color. Use Dark pages (the screen filter) or Invert for it.")
            has("libunity.so") || has("libil2cpp.so") -> AppKind("Unity app",
                "A game engine draws the screen. Color rules and overlays cannot change it. Use Dark pages (the screen filter) or Invert.")
            has("libgodot") || has("libcocos") -> AppKind("Game engine app",
                "The screen is drawn by the engine, not by Android views. Use Dark pages (the screen filter) or Invert.")
            has("assets/www/") || has("cordova") || has("capacitor") -> AppKind("Web app (WebView)",
                "The pages are web content. Color rules cannot reach web colors. Use Dark pages, Force dark, or the web black option.")
            has("androidx.compose") || has("compose.runtime") -> AppKind("Jetpack Compose app",
                "Colors are set in the code (Material theme), so overlays often change nothing. Auto (screen filter) reaches its greys; color rules need the LSPosed module.")
            // React Native draws its screen from a JavaScript bundle: colors are set in code, so resource overlays cannot reach them
            has("index.android.bundle") || has("libreactnativejni") || has("libhermes") -> AppKind("React Native app",
                "The screen is drawn from JavaScript, so color rules and overlays cannot reach it. Use Auto (screen filter) or Dark pages.")
            else -> AppKind("Native Android app", "Color rules, overlays and Dark pages can all reach it.")
        }
    } catch (_: Throwable) { null }
}
