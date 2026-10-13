package com.umutk.pulse.features

import android.content.Context

/** Each look's own wallpaper (a content uri the user picked) and the credit of its built-in one. */
private var appCtx: Context? = null

/** Called once from MainActivity.onCreate, before the splash reads the picks. */
fun initThemeWall(c: Context) { appCtx = c.applicationContext }

/** The user's wallpaper for this look (lookId = Look.theme.id), or null for the built-in one. */
fun themeWallpaper(lookId: String): String? =
    appCtx?.getSharedPreferences("p", Context.MODE_PRIVATE)?.getString("wall_$lookId", null)

fun setThemeWallpaper(c: Context, lookId: String, uri: String?) {
    c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putString("wall_$lookId", uri).apply()
}

/** Author and licence of each look's built-in wallpaper; null when there is nothing to credit. */
fun themeCredit(lookId: String): String? = when (lookId) {
    "win7" -> "Windows 7 wallpaper (Microsoft, supplied by the user)"
    "xp" -> "Bliss hill, Charles O'Rear, CC0 (Wikimedia Commons)"
    "material" -> "Android Nougat default wallpaper, Apache 2.0 (Wikimedia Commons)"
    "win11" -> "Windows 11 wallpaper (supplied by the user)"
    else -> null
}
