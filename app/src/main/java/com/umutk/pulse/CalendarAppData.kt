package com.umutk.pulse

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Settings of the Calendar app (own SharedPreferences, read once in [load]). */
object CalPrefs {
    private var sp: android.content.SharedPreferences? = null
    var look by mutableStateOf("material")      // a Pulse look id, see [looks]
    val looks = listOf("material", "oneui", "coloros", "win11", "win10", "win7", "xp", "win95")
    var dark by mutableStateOf("system")        // system / light / dark
    var dynamic by mutableStateOf(true)         // Material You colours
    var accent by mutableStateOf(0xFF2E6FD8.toInt())
    var amoled by mutableStateOf(false)
    var lang by mutableStateOf("en")
    var weekStart by mutableStateOf(1)          // java.time DayOfWeek value: 1 Mon, 7 Sun, 6 Sat
    var h24 by mutableStateOf(true)
    var startView by mutableStateOf("month")
    var hidden by mutableStateOf(setOf<String>()) // hidden calendar ids
    var defCal by mutableStateOf(-1L)
    var holShow by mutableStateOf(true)         // holidays from the internet (read-only entries)
    var holCountry by mutableStateOf("")        // ISO code, "" = the device's country
    var holPublic by mutableStateOf(true)
    var holObs by mutableStateOf(false)         // observances and other non-public days
    var defDur by mutableStateOf(60)            // default new-event length, minutes
    var defRem by mutableStateOf(10)            // default reminder, minutes before; -1 = none
    var weekNums by mutableStateOf(false)
    var hlWeekend by mutableStateOf(false)
    var showDeclined by mutableStateOf(true)
    var colorSrc by mutableStateOf("event")     // event / calendar
    var monthTitles by mutableStateOf(true)     // month cells: titles or dots
    var monthStyle by mutableStateOf("grid")    // grid (framed cells) / round (round days, no frames) / clean (no frames, a thin line between the weeks)
    var weekHour by mutableStateOf(7)           // hour the week/day grid opens at
    var weekZoom by mutableStateOf(48)          // hour height, dp
    var defVis by mutableStateOf(0)             // Events.ACCESS_*: 0 default, 1 confidential, 2 private, 3 public
    var defAvail by mutableStateOf(0)           // 0 busy, 1 free
    var goToday by mutableStateOf(true)
    var lastDay by mutableStateOf(0L)           // epoch day shown last, used when goToday is off

    fun load(c: Context) {
        val p = c.getSharedPreferences("pulse_calendar", 0); sp = p
        look = p.getString("look", look)!!.let { if (it == "fluent") "win11" else if (it == "classic") "win95" else it }; dark = p.getString("dark", dark)!!; dynamic = p.getBoolean("dynamic", dynamic)
        accent = p.getInt("accent", accent); amoled = p.getBoolean("amoled", amoled)
        lang = p.getString("lang", if (java.util.Locale.getDefault().language == "tr") "tr" else "en")!!
        weekStart = p.getInt("weekStart", weekStart); h24 = p.getBoolean("h24", h24); startView = p.getString("startView", startView)!!
        hidden = p.getStringSet("hidden", emptySet())!!.toSet(); defCal = p.getLong("defCal", -1L)
        holShow = p.getBoolean("holShow", true); holCountry = p.getString("holCountry", "")!!; holPublic = p.getBoolean("holPublic", true); holObs = p.getBoolean("holObs", false)
        defDur = p.getInt("defDur", 60); defRem = p.getInt("defRem", 10); weekNums = p.getBoolean("weekNums", false); hlWeekend = p.getBoolean("hlWeekend", false)
        showDeclined = p.getBoolean("showDeclined", true); colorSrc = p.getString("colorSrc", "event")!!; monthTitles = p.getBoolean("monthTitles", true); monthStyle = p.getString("monthStyle", "grid")!!
        weekHour = p.getInt("weekHour", 7); weekZoom = p.getInt("weekZoom", 48); defVis = p.getInt("defVis", 0); defAvail = p.getInt("defAvail", 0)
        goToday = p.getBoolean("goToday", true); lastDay = p.getLong("lastDay", 0L)
    }
    fun save() {
        sp?.edit()?.putString("look", look)?.putString("dark", dark)?.putBoolean("dynamic", dynamic)?.putInt("accent", accent)
            ?.putBoolean("amoled", amoled)?.putString("lang", lang)?.putInt("weekStart", weekStart)?.putBoolean("h24", h24)
            ?.putString("startView", startView)?.putStringSet("hidden", hidden)?.putLong("defCal", defCal)
            ?.putBoolean("holShow", holShow)?.putString("holCountry", holCountry)?.putBoolean("holPublic", holPublic)?.putBoolean("holObs", holObs)
            ?.putInt("defDur", defDur)?.putInt("defRem", defRem)?.putBoolean("weekNums", weekNums)?.putBoolean("hlWeekend", hlWeekend)?.putBoolean("showDeclined", showDeclined)
            ?.putString("colorSrc", colorSrc)?.putBoolean("monthTitles", monthTitles)?.putString("monthStyle", monthStyle)?.putInt("weekHour", weekHour)?.putInt("weekZoom", weekZoom)
            ?.putInt("defVis", defVis)?.putInt("defAvail", defAvail)?.putBoolean("goToday", goToday)?.putLong("lastDay", lastDay)?.apply()
        apply()
    }
    /** Pushes the choice into the shared Look (Cfg.load restores Pulse's own when the app closes). */
    fun apply() {
        Look.theme = Themes.first { it.id == (if (amoled && look == "material") "amoled" else look) }
        // Look.mode follows the in-app theme so T (dialogs, list rows) matches the Material colour scheme
        Look.mode = if (amoled && look == "material") "dark" else dark; Look.windowTheme = if (amoled && look == "material") "amoled" else "win"; Look.dynamic = dynamic; Look.accentIdx = -1
    }
}

private val TR = mapOf(
    "Calendar" to "Takvim", "Month" to "Ay", "Week" to "Hafta", "Day" to "Gün", "Year" to "Yıl", "Agenda" to "Ajanda",
    "Search" to "Ara", "Today" to "Bugün", "Settings" to "Ayarlar", "Calendars" to "Takvimler", "New event" to "Yeni etkinlik",
    "Edit event" to "Etkinliği düzenle", "Title" to "Başlık", "Location" to "Konum", "Description" to "Açıklama", "All day" to "Tüm gün",
    "Start" to "Başlangıç", "End" to "Bitiş", "Reminders" to "Hatırlatıcılar", "Repeat" to "Tekrar", "No repeat" to "Tekrarlama",
    "Daily" to "Günlük", "Weekly" to "Haftalık", "Monthly" to "Aylık", "Yearly" to "Yıllık", "Custom" to "Özel",
    "Save" to "Kaydet", "Cancel" to "İptal", "Delete" to "Sil", "Delete event?" to "Etkinlik silinsin mi?", "OK" to "Tamam",
    "At start" to "Başlangıçta", "No events" to "Etkinlik yok", "Allow calendar access" to "Takvim erişimine izin ver",
    "Look" to "Görünüm", "Theme" to "Tema",
    "System" to "Sistem", "Light" to "Açık", "Dark" to "Koyu", "Material You" to "Material You", "Accent" to "Vurgu",
    "AMOLED black" to "AMOLED siyah", "Language" to "Dil", "Week starts on" to "Hafta başlangıcı", "24-hour clock" to "24 saat",
    "Start view" to "Açılış görünümü", "Import .ics" to "İçe aktar .ics", "Export .ics" to "Dışa aktar .ics", "Mon" to "Pzt", "Sun" to "Paz", "Sat" to "Cmt",
    "Local calendar" to "Yerel takvim", "Add calendar" to "Takvim ekle", "Done" to "Bitti", "Imported" to "İçe aktarıldı", "Exported" to "Dışa aktarıldı",
    "Failed" to "Başarısız", "No writable calendar" to "Yazılabilir takvim yok", "Read-only calendar" to "Salt okunur takvim", "min" to "dk", "h" to "sa", "d" to "g",
    "before" to "önce", "Default calendar" to "Varsayılan takvim", "Untitled" to "Başlıksız", "Colours" to "Renkler", "Calendar" to "Takvim", "Data" to "Veri", "Holidays" to "Tatiller", "Show holidays" to "Tatilleri göster", "Country" to "Ülke", "Public holidays" to "Resmi tatiller", "Observances" to "Anma günleri", "Device country" to "Cihaz ülkesi")
private val TR2 = mapOf(
    "Search events" to "Etkinlik ara", "Results" to "Sonuçlar", "Clear" to "Temizle", "Events" to "Etkinlikler", "Views" to "Görünümler",
    "Default duration" to "Varsayılan süre", "Default reminder" to "Varsayılan hatırlatıcı", "None" to "Yok", "Week numbers" to "Hafta numaraları",
    "Highlight weekends" to "Hafta sonlarını vurgula", "Show declined events" to "Reddedilenleri göster", "Event colour" to "Etkinlik rengi",
    "Event" to "Etkinlik", "Month titles" to "Ay görünümünde başlıklar", "Month style" to "Ay görünümü stili", "Framed cells" to "Çerçeveli hücreler", "Round days" to "Yuvarlak günler", "No frames" to "Çerçevesiz", "Week starts at" to "Hafta görünümü başlangıç saati", "Zoom" to "Yakınlaştırma",
    "Small" to "Küçük", "Medium" to "Orta", "Large" to "Büyük", "Visibility" to "Görünürlük", "Availability" to "Müsaitlik", "Busy" to "Meşgul", "Free" to "Boş",
    "Default" to "Varsayılan", "Private" to "Özel", "Public" to "Herkese açık", "Confidential" to "Gizli", "Open on today" to "Açılışta bugüne git")
fun ct(s: String) = if (CalPrefs.lang == "tr") TR[s] ?: TR2[s] ?: s else s
fun calLocale(): java.util.Locale = java.util.Locale(CalPrefs.lang)

data class CalCal(val id: Long, val name: String, val account: String, val color: Int, val writable: Boolean)
data class CalEv(val id: Long, val calId: Long, val title: String, val loc: String, val desc: String, val start: Long, val end: Long,
                 val allDay: Boolean, val color: Int, val rrule: String?)

val calZone: ZoneId get() = ZoneId.systemDefault()
fun LocalDate.startMs(): Long = atStartOfDay(calZone).toInstant().toEpochMilli()
fun msToLdt(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), calZone)
fun ldtToMs(t: LocalDateTime): Long = t.atZone(calZone).toInstant().toEpochMilli()
/** Does the event touch [d]? End is exclusive, zero-length events count on their start day. */
fun CalEv.on(d: LocalDate): Boolean { val a = d.startMs(); val b = d.plusDays(1).startMs(); return start < b && maxOf(end, start + 1) > a }

object CalData {
    private val EVP = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE,
        CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_COLOR, CalendarContract.Instances.RRULE, CalendarContract.Instances.CALENDAR_COLOR, CalendarContract.Instances.SELF_ATTENDEE_STATUS)

    fun calendars(c: Context): List<CalCal> = runCatching {
        val out = ArrayList<CalCal>()
        c.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
            null, null, CalendarContract.Calendars.ACCOUNT_NAME)?.use {
            while (it.moveToNext()) out.add(CalCal(it.getLong(0), it.getString(1) ?: "", it.getString(2) ?: "", it.getInt(3), it.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR))
        }
        out
    }.getOrDefault(emptyList())

    /** Instances in [from, to) epoch ms, hidden calendars left out. All-day events are moved from UTC midnights to local midnights. */
    fun events(c: Context, from: Long, to: Long): List<CalEv> = runCatching {
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon(); ContentUris.appendId(b, from - 86400000L); ContentUris.appendId(b, to + 86400000L)
        val out = ArrayList<CalEv>()
        c.contentResolver.query(b.build(), EVP, null, null, CalendarContract.Instances.BEGIN)?.use {
            while (it.moveToNext()) {
                val cal = it.getLong(1); if (cal.toString() in CalPrefs.hidden) continue
                if (!CalPrefs.showDeclined && it.getInt(11) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                val all = it.getInt(7) == 1
                var s = it.getLong(5); var e = it.getLong(6)
                if (all) { s = utcDayToLocal(s); e = utcDayToLocal(e) }
                if (e <= from || s >= to) continue
                out.add(CalEv(it.getLong(0), cal, it.getString(2) ?: "", it.getString(3) ?: "", it.getString(4) ?: "", s, e, all, (if (CalPrefs.colorSrc == "calendar") 0 else it.getInt(8)).let { c -> if (c != 0) c else it.getInt(10) }, it.getString(9)))
            }
        }
        out
    }.getOrDefault(emptyList())

    private fun utcDayToLocal(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().startMs()
    private fun localDayToUtc(ms: Long): Long = msToLdt(ms).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun reminders(c: Context, eventId: Long): List<Int> = runCatching {
        val out = ArrayList<Int>()
        c.contentResolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf(CalendarContract.Reminders.MINUTES), "${CalendarContract.Reminders.EVENT_ID}=?", arrayOf("$eventId"), null)
            ?.use { while (it.moveToNext()) out.add(it.getInt(0).let { m -> if (m < 0) 0 else m }) }
        out
    }.getOrDefault(emptyList())

    /** Series start (DTSTART) of an event, for editing a repeating event from one of its instances. */
    fun seriesStart(c: Context, eventId: Long): Long? = runCatching {
        c.contentResolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), arrayOf(CalendarContract.Events.DTSTART, CalendarContract.Events.ALL_DAY), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0).let { s -> if (it.getInt(1) == 1) utcDayToLocal(s) else s } else null }
    }.getOrNull()

    /** Insert (id 0) or update an event; [s]/[e] are local epoch ms (all-day: local midnights, e exclusive). */
    fun save(c: Context, id: Long, cal: Long, title: String, loc: String, desc: String, allDay: Boolean, s: Long, e: Long, rrule: String?, reminders: List<Int>): Boolean = runCatching {
        val v = ContentValues()
        v.put(CalendarContract.Events.CALENDAR_ID, cal); v.put(CalendarContract.Events.TITLE, title)
        v.put(CalendarContract.Events.EVENT_LOCATION, loc); v.put(CalendarContract.Events.DESCRIPTION, desc)
        v.put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        v.put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else calZone.id)
        val ds = if (allDay) localDayToUtc(s) else s
        val de = if (allDay) localDayToUtc(maxOf(msToLdt(e).toLocalDate(), msToLdt(s).toLocalDate().plusDays(1)).startMs()) else maxOf(e, s)
        v.put(CalendarContract.Events.DTSTART, ds)
        if (rrule.isNullOrEmpty()) { v.put(CalendarContract.Events.DTEND, de); v.putNull(CalendarContract.Events.RRULE); v.putNull(CalendarContract.Events.DURATION) }
        else { v.put(CalendarContract.Events.RRULE, rrule); v.putNull(CalendarContract.Events.DTEND)
            v.put(CalendarContract.Events.DURATION, if (allDay) "P${maxOf(1L, (de - ds) / 86400000L)}D" else "PT${maxOf(0L, (de - ds) / 1000L)}S") }
        if (id == 0L) { v.put(CalendarContract.Events.ACCESS_LEVEL, CalPrefs.defVis); v.put(CalendarContract.Events.AVAILABILITY, CalPrefs.defAvail) }
        v.put(CalendarContract.Events.HAS_ALARM, if (reminders.isEmpty()) 0 else 1)
        val eid: Long
        if (id == 0L) eid = ContentUris.parseId(c.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)!!)
        else { eid = id; c.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), v, null, null)
            c.contentResolver.delete(CalendarContract.Reminders.CONTENT_URI, "${CalendarContract.Reminders.EVENT_ID}=?", arrayOf("$id")) }
        for (m in reminders.distinct()) c.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eid); put(CalendarContract.Reminders.MINUTES, m); put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT) })
        true
    }.getOrDefault(false)

    fun delete(c: Context, id: Long): Boolean = runCatching { c.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0 }.getOrDefault(false)

    /** A device-only calendar (no account), used when the phone has none or the user wants one. */
    fun addLocal(c: Context, name: String): Long = runCatching {
        val u = CalendarContract.Calendars.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "Pulse").appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        val v = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "Pulse"); put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, name); put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF2E6FD8.toInt()); put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "Pulse"); put(CalendarContract.Calendars.VISIBLE, 1); put(CalendarContract.Calendars.SYNC_EVENTS, 1) }
        ContentUris.parseId(c.contentResolver.insert(u, v)!!)
    }.getOrDefault(-1L)

    // ---- .ics ----
    private val UTCF = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val DAYF = DateTimeFormatter.ofPattern("yyyyMMdd")
    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n")
    private fun unesc(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    fun exportIcs(c: Context, out: Uri): Boolean = runCatching {
        val sb = StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Pulse//Calendar//EN\r\n")
        c.contentResolver.query(CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.DESCRIPTION, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION, CalendarContract.Events.ALL_DAY, CalendarContract.Events.RRULE), "${CalendarContract.Events.DELETED}=0", null, null)?.use {
            while (it.moveToNext()) {
                if (it.getLong(1).toString() in CalPrefs.hidden) continue
                val all = it.getInt(8) == 1; val s = it.getLong(5); val e = if (it.isNull(6)) null else it.getLong(6)
                fun line(n: String, ms: Long) = if (all) "$n;VALUE=DATE:" + Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).format(DAYF) else "$n:" + Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).format(UTCF)
                sb.append("BEGIN:VEVENT\r\nUID:pulse-${it.getLong(0)}@pulse\r\nDTSTAMP:").append(Instant.now().atZone(ZoneOffset.UTC).format(UTCF)).append("\r\n")
                sb.append("SUMMARY:").append(esc(it.getString(2) ?: "")).append("\r\n").append(line("DTSTART", s)).append("\r\n")
                if (e != null) sb.append(line("DTEND", e)).append("\r\n")
                else it.getString(7)?.let { d -> sb.append("DURATION:").append(d).append("\r\n") }
                it.getString(3)?.takeIf { x -> x.isNotEmpty() }?.let { x -> sb.append("LOCATION:").append(esc(x)).append("\r\n") }
                it.getString(4)?.takeIf { x -> x.isNotEmpty() }?.let { x -> sb.append("DESCRIPTION:").append(esc(x)).append("\r\n") }
                it.getString(9)?.takeIf { x -> x.isNotEmpty() }?.let { x -> sb.append("RRULE:").append(x).append("\r\n") }
                sb.append("END:VEVENT\r\n")
            }
        }
        sb.append("END:VCALENDAR\r\n")
        c.contentResolver.openOutputStream(out, "wt")!!.use { o -> o.write(sb.toString().toByteArray()) }
        true
    }.getOrDefault(false)

    /** Basic import: SUMMARY, LOCATION, DESCRIPTION, DTSTART, DTEND, RRULE (alarms and time zone ids are ignored). Returns the number added, -1 on failure. */
    fun importIcs(c: Context, src: Uri, cal: Long): Int = runCatching {
        val raw = c.contentResolver.openInputStream(src)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val lines = ArrayList<String>()
        for (l in raw.split("\r\n", "\n")) { if ((l.startsWith(" ") || l.startsWith("\t")) && lines.isNotEmpty()) lines[lines.lastIndex] = lines.last() + l.substring(1) else lines.add(l) }
        var n = 0; var m: HashMap<String, String>? = null; var props: HashMap<String, String>? = null
        for (l in lines) {
            if (l == "BEGIN:VEVENT") { m = HashMap(); props = HashMap(); continue }
            if (l == "END:VEVENT") {
                val v = m; val pr = props; m = null; props = null
                if (v != null && pr != null && v["DTSTART"] != null) {
                    val (s, all) = icsTime(v["DTSTART"]!!, pr["DTSTART"] ?: "")
                    val e = v["DTEND"]?.let { icsTime(it, pr["DTEND"] ?: "").first } ?: if (all) s + 86400000L else s
                    if (save(c, 0, cal, unesc(v["SUMMARY"] ?: ""), unesc(v["LOCATION"] ?: ""), unesc(v["DESCRIPTION"] ?: ""), all, s, e, v["RRULE"], emptyList())) n++
                }
                continue
            }
            if (m == null) continue
            val i = l.indexOf(':'); if (i < 0) continue
            val head = l.substring(0, i); val key = head.substringBefore(';').uppercase()
            m[key] = l.substring(i + 1); props!![key] = head.substringAfter(';', "")
        }
        n
    }.getOrDefault(-1)

    /** (local epoch ms, allDay) from an ICS value; Z = UTC, otherwise local ("floating"). */
    private fun icsTime(v: String, params: String): Pair<Long, Boolean> {
        if (params.contains("VALUE=DATE") && !params.contains("DATE-TIME") || v.length == 8) return LocalDate.parse(v.take(8), DAYF).startMs() to true
        val t = LocalDateTime.parse(v.take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        return (if (v.endsWith("Z")) t.toInstant(ZoneOffset.UTC).toEpochMilli() else ldtToMs(t)) to false
    }
}
