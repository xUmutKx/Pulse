package com.umutk.pulse

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** Public holidays from date.nager.at (no key), cached per country and year in filesDir/holidays, refreshed at most weekly. Read-only entries, never written to the provider. */
object CalHolidays {
    const val CAL_ID = -2L
    private const val WEEK = 7L * 24 * 3600 * 1000
    val PUBLIC_COLOR = 0xFFD84315.toInt()
    val OBS_COLOR = 0xFF8E24AA.toInt()
    val countries = listOf("TR", "DE", "GB", "US", "FR", "NL", "IT", "ES", "AT", "CH", "BE", "GR", "AZ", "CY", "BG", "RO", "PL", "SE", "NO", "DK", "FI", "IE", "PT", "CA", "AU", "JP", "BR", "MX")

    fun country(): String = CalPrefs.holCountry.ifEmpty { java.util.Locale.getDefault().country.uppercase().takeIf { it.length == 2 } ?: "TR" }
    fun countryName(cc: String): String = java.util.Locale("", cc).getDisplayCountry(calLocale()).ifEmpty { cc }

    /** Blocking (network on a cache miss): call off the main thread. */
    fun events(ctx: Context, from: Long, to: Long): List<CalEv> {
        if (!CalPrefs.holShow || !(CalPrefs.holPublic || CalPrefs.holObs)) return emptyList()
        val cc = country(); val out = ArrayList<CalEv>()
        for (y in msToLdt(from).year..msToLdt(to - 1).year) {
            val arr = load(ctx, cc, y) ?: continue
            for (i in 0 until arr.length()) runCatching {
                val o = arr.getJSONObject(i)
                val ty = o.optJSONArray("types")?.let { t -> (0 until t.length()).map { t.getString(it) } } ?: listOf("Public")
                val pub = "Public" in ty
                if (if (pub) !CalPrefs.holPublic else !CalPrefs.holObs) return@runCatching
                val d = LocalDate.parse(o.getString("date"))
                val name = if (CalPrefs.lang == "tr" || o.optString("name").isEmpty()) o.optString("localName").ifEmpty { o.optString("name") } else o.optString("name")
                val s = d.startMs()
                if (s + 86400000L > from && s < to) out.add(CalEv(-(s / 86400000L) * 100 - i - 10, CAL_ID, name, "", "", s, d.plusDays(1).startMs(), true, if (pub) PUBLIC_COLOR else OBS_COLOR, null))
            }
        }
        return out
    }

    private fun load(ctx: Context, cc: String, y: Int): JSONArray? {
        val f = File(ctx.filesDir, "holidays/${cc}_$y.json")
        val cached = runCatching { if (f.isFile) JSONArray(f.readText()) else null }.getOrNull()
        if (cached != null && System.currentTimeMillis() - f.lastModified() < WEEK) return cached
        return runCatching {
            val c = URL("https://date.nager.at/api/v3/PublicHolidays/$y/$cc").openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            val txt = try { if (c.responseCode != 200) throw java.io.IOException("${c.responseCode}"); c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
            val a = JSONArray(txt)
            f.parentFile?.mkdirs(); f.writeText(txt)
            a
        }.getOrElse { cached } // offline or failed: the stale copy
    }
}
