package com.umutk.pulse

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract as CC
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream

// Pulse Contacts: data layer (ContactsContract), vCard, preferences. UI is in ContactsAppUi.kt, the entry in ContactsActivity.kt.

/** One row of the list. */
class CtContact(val id: Long, val display: String, val given: String, val family: String, val starred: Boolean, val photo: String?, val nums: String,
    val prefix: String = "", val middle: String = "", val suffix: String = "", val emails: String = "")

/** type: phone/email/address type code of ContactsContract (see ctTypes). */
data class CtField(val type: Int, val value: String)

class CtDetail(
    val id: Long, val raws: List<Long>, val display: String, val given: String, val family: String, val org: String,
    val starred: Boolean, val photo: String?, val phones: List<CtField>, val emails: List<CtField>, val addrs: List<CtField>,
    val birthday: String, val note: String, val groups: Set<Long>,
    val prefix: String = "", val middle: String = "", val suffix: String = "", val nick: String = "", val lookup: String = "",
)

class CtGroup(val id: Long, val title: String)

object CtPrefs {
    var look by mutableStateOf("material")
    var dynamic by mutableStateOf(true)
    var accent by mutableStateOf(-1)
    var amoled by mutableStateOf(false)
    var lang by mutableStateOf("en")
    var firstFirst by mutableStateOf(true)
    var sortBy by mutableStateOf("first")
    var fade by mutableStateOf(true)
    // extra name fields: shown in the editor / shown in names (an existing value always shows in the editor)
    var showPrefix by mutableStateOf(false); var showMiddle by mutableStateOf(false); var showSuffix by mutableStateOf(false); var showNick by mutableStateOf(false)
    var dispPrefix by mutableStateOf(false); var dispMiddle by mutableStateOf(false); var dispSuffix by mutableStateOf(false)
    val looks = listOf("material", "oneui", "coloros", "win11", "win10", "win7", "xp", "win95")

    private fun sp(c: Context) = c.getSharedPreferences("contacts_app", Context.MODE_PRIVATE)
    fun load(c: Context) {
        val p = sp(c)
        look = p.getString("look", "material") ?: "material"; dynamic = p.getBoolean("dynamic", true); accent = p.getInt("accent", -1)
        amoled = p.getBoolean("amoled", false); lang = p.getString("lang", "en") ?: "en"
        firstFirst = p.getBoolean("firstFirst", true); sortBy = p.getString("sortBy", "first") ?: "first"; fade = p.getBoolean("fade", true)
        showPrefix = p.getBoolean("showPrefix", false); showMiddle = p.getBoolean("showMiddle", false); showSuffix = p.getBoolean("showSuffix", false); showNick = p.getBoolean("showNick", false)
        dispPrefix = p.getBoolean("dispPrefix", false); dispMiddle = p.getBoolean("dispMiddle", false); dispSuffix = p.getBoolean("dispSuffix", false)
    }
    fun save(c: Context) {
        sp(c).edit().putString("look", look).putBoolean("dynamic", dynamic).putInt("accent", accent).putBoolean("amoled", amoled)
            .putString("lang", lang).putBoolean("firstFirst", firstFirst).putString("sortBy", sortBy).putBoolean("fade", fade)
            .putBoolean("showPrefix", showPrefix).putBoolean("showMiddle", showMiddle).putBoolean("showSuffix", showSuffix).putBoolean("showNick", showNick)
            .putBoolean("dispPrefix", dispPrefix).putBoolean("dispMiddle", dispMiddle).putBoolean("dispSuffix", dispSuffix).apply()
        apply()
    }
    /** Pushes the choice into the shared Look (Cfg.load restores Pulse's own when this app closes). */
    fun apply() {
        Look.theme = Themes.first { it.id == if (amoled && look == "material") "amoled" else look }
        Look.mode = "system"; Look.windowTheme = "win"; Look.dynamic = dynamic; Look.accentIdx = accent
    }
}

fun ctr(en: String, turkish: String) = if (CtPrefs.lang == "tr") turkish else en

fun CtContact.label(): String = ctName(prefix, given, middle, family, suffix, display)

/** Name in the chosen order; prefix / middle / suffix only when their display switch is on. */
internal fun ctName(prefix: String, given: String, middle: String, family: String, suffix: String, display: String): String {
    if (given.isBlank() && family.isBlank()) return display
    val m = if (CtPrefs.dispMiddle) middle else ""
    val core = if (CtPrefs.firstFirst) listOf(given, m, family) else listOf(family, given, m)
    return (listOf(if (CtPrefs.dispPrefix) prefix else "") + core + listOf(if (CtPrefs.dispSuffix) suffix else "")).filter { it.isNotBlank() }.joinToString(" ")
}

private fun CtContact.sortKey(): String =
    (if (CtPrefs.sortBy == "last") family.ifBlank { display.substringAfterLast(' ') } else given.ifBlank { display }).lowercase()

internal fun ctSorted(l: List<CtContact>): List<CtContact> = l.sortedWith(compareBy<CtContact> { it.sortKey() }.thenBy { it.display.lowercase() })

internal fun ctLetter(c: CtContact): String = c.sortKey().firstOrNull()?.takeIf { it.isLetter() }?.uppercase() ?: "#"

/** Phone, email, address types in the order the editor cycles through them. */
internal fun ctTypes(kind: Int): List<Int> = when (kind) { 0 -> listOf(2, 1, 3, 7); 1 -> listOf(1, 2, 4, 3); else -> listOf(1, 2, 3) }
internal fun ctTypeLabel(kind: Int, t: Int): String = when {
    kind == 0 && t == 2 || kind == 1 && t == 4 -> ctr("Mobile", "Cep")
    t == 1 -> ctr("Home", "Ev"); t == 2 -> ctr("Work", "İş"); kind == 0 && t == 3 -> ctr("Work", "İş")
    else -> ctr("Other", "Diğer")
}

// ------------------------------------------------------------------------------------------------------------ queries

internal fun ctLoadAll(ctx: Context): List<CtContact> {
    val r = ctx.contentResolver
    val names = HashMap<Long, List<String>>()   // given, family, prefix, middle, suffix
    runCatching {
        r.query(CC.Data.CONTENT_URI, arrayOf(CC.Data.CONTACT_ID, StructuredName.GIVEN_NAME, StructuredName.FAMILY_NAME, StructuredName.PREFIX, StructuredName.MIDDLE_NAME, StructuredName.SUFFIX),
            "${CC.Data.MIMETYPE}=?", arrayOf(StructuredName.CONTENT_ITEM_TYPE), null)?.use { c ->
            while (c.moveToNext()) names.getOrPut(c.getLong(0)) { (1..5).map { i -> c.getString(i) ?: "" } }
        }
    }
    val nums = HashMap<Long, StringBuilder>()
    runCatching {
        r.query(Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER), null, null, null)?.use { c ->
            while (c.moveToNext()) nums.getOrPut(c.getLong(0)) { StringBuilder() }.append(c.getString(1) ?: "").append('\n')
        }
    }
    val mails = HashMap<Long, StringBuilder>()
    runCatching {
        r.query(CC.CommonDataKinds.Email.CONTENT_URI, arrayOf(CC.CommonDataKinds.Email.CONTACT_ID, CC.CommonDataKinds.Email.ADDRESS), null, null, null)?.use { c ->
            while (c.moveToNext()) mails.getOrPut(c.getLong(0)) { StringBuilder() }.append(c.getString(1) ?: "").append('\n')
        }
    }
    val out = ArrayList<CtContact>()
    runCatching {
        r.query(CC.Contacts.CONTENT_URI, arrayOf(CC.Contacts._ID, CC.Contacts.DISPLAY_NAME_PRIMARY, CC.Contacts.STARRED, CC.Contacts.PHOTO_THUMBNAIL_URI),
            null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0); val n = names[id]
                out.add(CtContact(id, c.getString(1)?.takeIf { it.isNotBlank() } ?: "?", n?.get(0) ?: "", n?.get(1) ?: "", c.getInt(2) == 1, c.getString(3), nums[id]?.toString() ?: "",
                    n?.get(2) ?: "", n?.get(3) ?: "", n?.get(4) ?: "", mails[id]?.toString() ?: ""))
            }
        }
    }
    return out
}

private class CtAcc(var display: String = "", var starred: Boolean = false, var photo: String? = null) {
    var given = ""; var family = ""; var org = ""; var birthday = ""; var note = ""; var prefix = ""; var middle = ""; var suffix = ""; var nick = ""; var lookup = ""
    val raws = ArrayList<Long>(); val phones = ArrayList<CtField>(); val emails = ArrayList<CtField>(); val addrs = ArrayList<CtField>(); val groups = HashSet<Long>()
}

/** [ids] null = every contact. */
internal fun ctLoadDetails(ctx: Context, ids: Set<Long>?): List<CtDetail> {
    val r = ctx.contentResolver
    val inList = ids?.joinToString(",")
    val acc = LinkedHashMap<Long, CtAcc>()
    r.query(CC.Contacts.CONTENT_URI, arrayOf(CC.Contacts._ID, CC.Contacts.DISPLAY_NAME_PRIMARY, CC.Contacts.STARRED, CC.Contacts.PHOTO_URI, CC.Contacts.LOOKUP_KEY),
        inList?.let { "${CC.Contacts._ID} IN ($it)" }, null, null)?.use { c ->
        while (c.moveToNext()) acc[c.getLong(0)] = CtAcc(c.getString(1) ?: "", c.getInt(2) == 1, c.getString(3)).also { it.lookup = c.getString(4) ?: "" }
    }
    r.query(CC.RawContacts.CONTENT_URI, arrayOf(CC.RawContacts._ID, CC.RawContacts.CONTACT_ID),
        "${CC.RawContacts.DELETED}=0" + (inList?.let { " AND ${CC.RawContacts.CONTACT_ID} IN ($it)" } ?: ""), null, "${CC.RawContacts._ID} ASC")?.use { c ->
        while (c.moveToNext()) acc[c.getLong(1)]?.raws?.add(c.getLong(0))
    }
    r.query(CC.Data.CONTENT_URI, arrayOf(CC.Data.CONTACT_ID, CC.Data.MIMETYPE, CC.Data.DATA1, CC.Data.DATA2, CC.Data.DATA3, CC.Data.DATA4, CC.Data.DATA5, CC.Data.DATA6),
        inList?.let { "${CC.Data.CONTACT_ID} IN ($it)" }, null, null)?.use { c ->
        while (c.moveToNext()) {
            val a = acc[c.getLong(0)] ?: continue
            val d1 = c.getString(2) ?: ""; val d2 = c.getString(3); val t = d2?.toIntOrNull() ?: 7
            when (c.getString(1)) {
                StructuredName.CONTENT_ITEM_TYPE -> if (a.given.isEmpty() && a.family.isEmpty()) {
                    a.given = d2 ?: ""; a.family = c.getString(4) ?: ""; a.prefix = c.getString(5) ?: ""; a.middle = c.getString(6) ?: ""; a.suffix = c.getString(7) ?: ""
                }
                Nickname.CONTENT_ITEM_TYPE -> if (a.nick.isEmpty()) a.nick = d1
                Phone.CONTENT_ITEM_TYPE -> if (d1.isNotBlank()) a.phones.add(CtField(t, d1))
                Email.CONTENT_ITEM_TYPE -> if (d1.isNotBlank()) a.emails.add(CtField(t, d1))
                StructuredPostal.CONTENT_ITEM_TYPE -> if (d1.isNotBlank()) a.addrs.add(CtField(t, d1))
                Event.CONTENT_ITEM_TYPE -> if (t == 3 && a.birthday.isEmpty()) a.birthday = d1
                Note.CONTENT_ITEM_TYPE -> if (a.note.isEmpty()) a.note = d1
                Organization.CONTENT_ITEM_TYPE -> if (a.org.isEmpty()) a.org = d1
                GroupMembership.CONTENT_ITEM_TYPE -> d1.toLongOrNull()?.let { a.groups.add(it) }
            }
        }
    }
    return acc.map { (id, a) -> CtDetail(id, a.raws, a.display, a.given, a.family, a.org, a.starred, a.photo, a.phones, a.emails, a.addrs, a.birthday, a.note, a.groups, a.prefix, a.middle, a.suffix, a.nick, a.lookup) }
}

internal fun ctSetStar(ctx: Context, id: Long, on: Boolean) {
    runCatching { ctx.contentResolver.update(android.content.ContentUris.withAppendedId(CC.Contacts.CONTENT_URI, id), ContentValues().apply { put(CC.Contacts.STARRED, if (on) 1 else 0) }, null, null) }
}

internal fun ctDelete(ctx: Context, id: Long) {
    runCatching { ctx.contentResolver.delete(android.content.ContentUris.withAppendedId(CC.Contacts.CONTENT_URI, id), null, null) }
}

/** Longest edge 720 px, JPEG. */
internal fun ctPhotoBitmap(ctx: Context, uri: Uri): Bitmap? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1; while (maxOf(o.outWidth, o.outHeight) / s > 1440) s *= 2
    val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return@runCatching null
    val k = 720f / maxOf(b.width, b.height)
    if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
}.getOrNull()

internal fun ctJpeg(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()

/** Writes [d] (id 0 or no raws = new contact). Only the fields the editor shows are replaced; groups and other data stay. */
internal fun ctSave(ctx: Context, d: CtDetail, photo: ByteArray?, clearPhoto: Boolean): Boolean = runCatching {
    val ops = ArrayList<ContentProviderOperation>()
    val isNew = d.raws.isEmpty()
    if (isNew) ops.add(ContentProviderOperation.newInsert(CC.RawContacts.CONTENT_URI).withValue(CC.RawContacts.ACCOUNT_TYPE, null).withValue(CC.RawContacts.ACCOUNT_NAME, null).build())
    else {
        val ids = d.raws.joinToString(",")
        val mimes = mutableListOf(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE)
        if (photo != null || clearPhoto) mimes.add(Photo.CONTENT_ITEM_TYPE)
        for (m in mimes) ops.add(ContentProviderOperation.newDelete(CC.Data.CONTENT_URI).withSelection("${CC.Data.RAW_CONTACT_ID} IN ($ids) AND ${CC.Data.MIMETYPE}=?", arrayOf(m)).build())
        ops.add(ContentProviderOperation.newDelete(CC.Data.CONTENT_URI).withSelection("${CC.Data.RAW_CONTACT_ID} IN ($ids) AND ${CC.Data.MIMETYPE}=? AND ${CC.Data.DATA2}=3", arrayOf(Event.CONTENT_ITEM_TYPE)).build())
    }
    fun ins(mime: String): ContentProviderOperation.Builder {
        val b = ContentProviderOperation.newInsert(CC.Data.CONTENT_URI)
        if (isNew) b.withValueBackReference(CC.Data.RAW_CONTACT_ID, 0) else b.withValue(CC.Data.RAW_CONTACT_ID, d.raws[0])
        return b.withValue(CC.Data.MIMETYPE, mime)
    }
    if (listOf(d.given, d.family, d.prefix, d.middle, d.suffix).any { it.isNotBlank() }) ops.add(ins(StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.GIVEN_NAME, d.given).withValue(StructuredName.FAMILY_NAME, d.family)
        .withValue(StructuredName.PREFIX, d.prefix).withValue(StructuredName.MIDDLE_NAME, d.middle).withValue(StructuredName.SUFFIX, d.suffix).build())
    if (d.nick.isNotBlank()) ops.add(ins(Nickname.CONTENT_ITEM_TYPE).withValue(Nickname.NAME, d.nick).withValue(Nickname.TYPE, Nickname.TYPE_DEFAULT).build())
    d.phones.filter { it.value.isNotBlank() }.forEach { ops.add(ins(Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER, it.value).withValue(Phone.TYPE, it.type).build()) }
    d.emails.filter { it.value.isNotBlank() }.forEach { ops.add(ins(Email.CONTENT_ITEM_TYPE).withValue(Email.ADDRESS, it.value).withValue(Email.TYPE, it.type).build()) }
    d.addrs.filter { it.value.isNotBlank() }.forEach { ops.add(ins(StructuredPostal.CONTENT_ITEM_TYPE).withValue(StructuredPostal.FORMATTED_ADDRESS, it.value).withValue(StructuredPostal.TYPE, it.type).build()) }
    if (d.birthday.isNotBlank()) ops.add(ins(Event.CONTENT_ITEM_TYPE).withValue(Event.START_DATE, d.birthday).withValue(Event.TYPE, 3).build())
    if (d.note.isNotBlank()) ops.add(ins(Note.CONTENT_ITEM_TYPE).withValue(Note.NOTE, d.note).build())
    if (photo != null) ops.add(ins(Photo.CONTENT_ITEM_TYPE).withValue(Photo.PHOTO, photo).build())
    ctx.contentResolver.applyBatch(CC.AUTHORITY, ops)
    true
}.getOrDefault(false)

// ------------------------------------------------------------------------------------------------------------ call log

/** type: CallLog.Calls.TYPE (1 in, 2 out, 3 missed, 5 rejected...). */
class CtCall(val number: String, val name: String?, val type: Int, val date: Long)

/** Needs READ_CALL_LOG. Newest 300. */
internal fun ctLoadCalls(ctx: Context): List<CtCall> {
    val out = ArrayList<CtCall>()
    runCatching {
        ctx.contentResolver.query(android.provider.CallLog.Calls.CONTENT_URI, arrayOf(android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.CACHED_NAME,
            android.provider.CallLog.Calls.TYPE, android.provider.CallLog.Calls.DATE), null, null, "${android.provider.CallLog.Calls.DATE} DESC")?.use { c ->
            while (c.moveToNext() && out.size < 300) out.add(CtCall(c.getString(0) ?: "", c.getString(1)?.takeIf { it.isNotBlank() }, c.getInt(2), c.getLong(3)))
        }
    }
    return out
}

// ------------------------------------------------------------------------------------------------------------ groups

internal fun ctLoadGroups(ctx: Context): Pair<List<CtGroup>, Map<Long, Set<Long>>> {
    val r = ctx.contentResolver
    val gs = ArrayList<CtGroup>()
    runCatching {
        r.query(CC.Groups.CONTENT_URI, arrayOf(CC.Groups._ID, CC.Groups.TITLE), "${CC.Groups.DELETED}=0", null, "${CC.Groups.TITLE} ASC")?.use { c ->
            while (c.moveToNext()) c.getString(1)?.takeIf { it.isNotBlank() }?.let { gs.add(CtGroup(c.getLong(0), it)) }
        }
    }
    val mem = HashMap<Long, MutableSet<Long>>()
    runCatching {
        r.query(CC.Data.CONTENT_URI, arrayOf(CC.Data.CONTACT_ID, CC.Data.DATA1), "${CC.Data.MIMETYPE}=?", arrayOf(GroupMembership.CONTENT_ITEM_TYPE), null)?.use { c ->
            while (c.moveToNext()) c.getString(1)?.toLongOrNull()?.let { mem.getOrPut(it) { HashSet() }.add(c.getLong(0)) }
        }
    }
    // the same title from several accounts shows once
    return gs.distinctBy { it.title.lowercase() } to mem
}

internal fun ctAddGroup(ctx: Context, title: String): Boolean = runCatching {
    ctx.contentResolver.insert(CC.Groups.CONTENT_URI, ContentValues().apply { put(CC.Groups.TITLE, title); put(CC.Groups.GROUP_VISIBLE, 1) }) != null
}.getOrDefault(false)

internal fun ctDeleteGroup(ctx: Context, id: Long) {
    runCatching { ctx.contentResolver.delete(android.content.ContentUris.withAppendedId(CC.Groups.CONTENT_URI, id), null, null) }
}

internal fun ctSetGroup(ctx: Context, rawId: Long, gid: Long, on: Boolean) {
    runCatching {
        if (on) ctx.contentResolver.insert(CC.Data.CONTENT_URI, ContentValues().apply {
            put(CC.Data.RAW_CONTACT_ID, rawId); put(CC.Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE); put(GroupMembership.GROUP_ROW_ID, gid)
        }) else ctx.contentResolver.delete(CC.Data.CONTENT_URI, "${CC.Data.RAW_CONTACT_ID}=? AND ${CC.Data.MIMETYPE}=? AND ${CC.Data.DATA1}=?",
            arrayOf(rawId.toString(), GroupMembership.CONTENT_ITEM_TYPE, gid.toString()))
    }
}

// ------------------------------------------------------------------------------------------------------------ duplicates

/** Contacts with the same name or the same number (last 8 digits), clustered. shortcut: exact match only, no fuzzy names. */
internal fun ctDuplicates(all: List<CtContact>): List<List<CtContact>> {
    val parent = HashMap<Long, Long>()
    fun find(x: Long): Long { var a = x; while (parent.getOrPut(a) { a } != a) a = parent[a]!!; return a }
    fun union(a: Long, b: Long) { parent[find(a)] = find(b) }
    val seen = HashMap<String, Long>()
    for (c in all) {
        val keys = ArrayList<String>()
        c.display.trim().lowercase().takeIf { it.length > 1 && it != "?" }?.let { keys.add("n:$it") }
        c.nums.split('\n').map { it.filter(Char::isDigit) }.filter { it.length >= 7 }.forEach { keys.add("p:" + it.takeLast(8)) }
        for (k in keys) { val o = seen[k]; if (o == null) seen[k] = c.id else union(o, c.id) }
    }
    return all.groupBy { find(it.id) }.values.filter { it.size > 1 }
}

internal fun ctMerge(ctx: Context, ids: List<Long>): Boolean = runCatching {
    val raw = ArrayList<Long>()
    ctx.contentResolver.query(CC.RawContacts.CONTENT_URI, arrayOf(CC.RawContacts._ID), "${CC.RawContacts.DELETED}=0 AND ${CC.RawContacts.CONTACT_ID} IN (${ids.joinToString(",")})", null, null)?.use { c ->
        while (c.moveToNext()) raw.add(c.getLong(0))
    }
    for (i in 1 until raw.size) ctx.contentResolver.update(CC.AggregationExceptions.CONTENT_URI, ContentValues().apply {
        put(CC.AggregationExceptions.TYPE, CC.AggregationExceptions.TYPE_KEEP_TOGETHER)
        put(CC.AggregationExceptions.RAW_CONTACT_ID1, raw[0]); put(CC.AggregationExceptions.RAW_CONTACT_ID2, raw[i])
    }, null, null)
    raw.size > 1
}.getOrDefault(false)

// ------------------------------------------------------------------------------------------------------------ vCard

private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")
private fun unesc(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

internal fun ctVcf(list: List<CtDetail>): String {
    val sb = StringBuilder()
    for (d in list) {
        sb.append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        sb.append("N:${esc(d.family)};${esc(d.given)};${esc(d.middle)};${esc(d.prefix)};${esc(d.suffix)}\r\nFN:${esc(listOf(d.prefix, d.given, d.middle, d.family, d.suffix).filter { it.isNotBlank() }.joinToString(" ").ifBlank { d.display })}\r\n")
        if (d.nick.isNotBlank()) sb.append("NICKNAME:${esc(d.nick)}\r\n")
        if (d.org.isNotBlank()) sb.append("ORG:${esc(d.org)}\r\n")
        d.phones.forEach { sb.append("TEL;TYPE=${when (it.type) { 1 -> "HOME"; 3 -> "WORK"; 2 -> "CELL"; else -> "OTHER" }}:${it.value}\r\n") }
        d.emails.forEach { sb.append("EMAIL;TYPE=${when (it.type) { 1 -> "HOME"; 2 -> "WORK"; 4 -> "CELL"; else -> "OTHER" }}:${it.value}\r\n") }
        d.addrs.forEach { sb.append("ADR;TYPE=${when (it.type) { 1 -> "HOME"; 2 -> "WORK"; else -> "OTHER" }}:;;${esc(it.value)};;;;\r\n") }
        if (d.birthday.isNotBlank()) sb.append("BDAY:${d.birthday}\r\n")
        if (d.note.isNotBlank()) sb.append("NOTE:${esc(d.note)}\r\n")
        sb.append("END:VCARD\r\n")
    }
    return sb.toString()
}

/** shortcut: no PHOTO, no vCard 4 extras, quoted-printable only for plain ASCII/UTF-8; upgrade when real files need it. */
internal fun ctParseVcf(text: String): List<CtDetail> {
    val lines = ArrayList<String>()
    for (l in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
        if ((l.startsWith(" ") || l.startsWith("\t")) && lines.isNotEmpty()) lines[lines.lastIndex] = lines.last() + l.substring(1) else lines.add(l)
    }
    val out = ArrayList<CtDetail>()
    var given = ""; var family = ""; var fn = ""; var org = ""; var bday = ""; var note = ""; var pre = ""; var mid = ""; var suf = ""; var nick = ""
    var ph = ArrayList<CtField>(); var em = ArrayList<CtField>(); var ad = ArrayList<CtField>()
    for (line in lines) {
        val i = line.indexOf(':'); if (i < 0) continue
        val head = line.substring(0, i).uppercase(); var v = line.substring(i + 1)
        val name = head.substringBefore(';').substringAfterLast('.')
        if (head.contains("QUOTED-PRINTABLE")) v = runCatching {
            val bytes = ByteArrayOutputStream(); var k = 0
            while (k < v.length) { if (v[k] == '=' && k + 2 < v.length) { bytes.write(v.substring(k + 1, k + 3).toInt(16)); k += 3 } else { bytes.write(v[k].code); k++ } }
            String(bytes.toByteArray(), Charsets.UTF_8)
        }.getOrDefault(v)
        fun t(vararg m: Pair<String, Int>) = m.firstOrNull { head.contains(it.first) }?.second ?: 7
        when (name) {
            "BEGIN" -> { given = ""; family = ""; pre = ""; mid = ""; suf = ""; nick = ""; fn = ""; org = ""; bday = ""; note = ""; ph = ArrayList(); em = ArrayList(); ad = ArrayList() }
            "N" -> { val p = v.split(';'); family = unesc(p.getOrElse(0) { "" }); given = unesc(p.getOrElse(1) { "" })
                mid = unesc(p.getOrElse(2) { "" }); pre = unesc(p.getOrElse(3) { "" }); suf = unesc(p.getOrElse(4) { "" }) }
            "NICKNAME" -> nick = unesc(v)
            "FN" -> fn = unesc(v)
            "ORG" -> org = unesc(v.split(';').filter { it.isNotBlank() }.joinToString(", "))
            "TEL" -> if (v.isNotBlank()) ph.add(CtField(t("CELL" to 2, "HOME" to 1, "WORK" to 3), v.trim()))
            "EMAIL" -> if (v.isNotBlank()) em.add(CtField(t("HOME" to 1, "WORK" to 2, "CELL" to 4), v.trim()))
            "ADR" -> v.split(';').map { unesc(it) }.filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let { ad.add(CtField(t("HOME" to 1, "WORK" to 2), it)) }
            "BDAY" -> bday = v.trim().let { if (it.length == 8 && it.all(Char::isDigit)) "${it.take(4)}-${it.substring(4, 6)}-${it.substring(6)}" else it }
            "NOTE" -> note = unesc(v)
            "END" -> if (given.isNotBlank() || family.isNotBlank() || fn.isNotBlank() || ph.isNotEmpty() || em.isNotEmpty()) {
                if (given.isBlank() && family.isBlank() && fn.isNotBlank()) { given = fn.substringBeforeLast(' ', fn); family = if (fn.contains(' ')) fn.substringAfterLast(' ') else "" }
                out.add(CtDetail(0, emptyList(), fn, given, family, org, false, null, ph, em, ad, bday, note, emptySet(), pre, mid, suf, nick))
            }
        }
    }
    return out
}

// ------------------------------------------------------------------------------------------------------------ call backgrounds

/** A contact's chosen call background: a copy in filesDir/callbg. Kept by the contact's LOOKUP_KEY (survives re-aggregation). */
class CtBg(val path: String, val video: Boolean)

private fun ctBgPrefs(c: Context) = c.getSharedPreferences("contacts_app", Context.MODE_PRIVATE)

internal fun ctBgAll(ctx: Context): Map<String, CtBg> = runCatching {
    val o = org.json.JSONObject(ctBgPrefs(ctx).getString("callbg", "{}") ?: "{}")
    o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { j -> j.optString("p").takeIf { p -> java.io.File(p).exists() }?.let { p -> k to CtBg(p, j.optBoolean("v")) } } }.toMap()
}.getOrDefault(emptyMap())

private fun ctBgStore(ctx: Context, m: Map<String, CtBg>) {
    val o = org.json.JSONObject()
    m.forEach { (k, b) -> o.put(k, org.json.JSONObject().put("p", b.path).put("v", b.video)) }
    ctBgPrefs(ctx).edit().putString("callbg", o.toString()).apply()
}

/** Copies [uri] (image or video) into the app and points [lookup] at it; the old copy goes away. */
internal fun ctBgSet(ctx: Context, lookup: String, uri: Uri): Boolean = runCatching {
    val mime = ctx.contentResolver.getType(uri) ?: ""
    val video = mime.startsWith("video")
    val dir = java.io.File(ctx.filesDir, "callbg").also { it.mkdirs() }
    val f = java.io.File(dir, "${System.currentTimeMillis()}." + (mime.substringAfter('/', if (video) "mp4" else "jpg").substringBefore(';').ifBlank { "bin" }))
    ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    val m = ctBgAll(ctx).toMutableMap()
    m.put(lookup, CtBg(f.path, video))?.let { java.io.File(it.path).delete() }
    ctBgStore(ctx, m)
    true
}.getOrDefault(false)

internal fun ctBgClear(ctx: Context, lookup: String) {
    val m = ctBgAll(ctx).toMutableMap()
    m.remove(lookup)?.let { java.io.File(it.path).delete() }
    ctBgStore(ctx, m)
}

internal fun ctLookup(ctx: Context, id: Long): String = runCatching {
    ctx.contentResolver.query(android.content.ContentUris.withAppendedId(CC.Contacts.CONTENT_URI, id), arrayOf(CC.Contacts.LOOKUP_KEY), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull() ?: ""

/** Contacts that have a call background: (lookup key, name, background). */
internal fun ctBgContacts(ctx: Context): List<Triple<String, String, CtBg>> {
    val m = ctBgAll(ctx); if (m.isEmpty()) return emptyList()
    val out = ArrayList<Triple<String, String, CtBg>>()
    runCatching {
        ctx.contentResolver.query(CC.Contacts.CONTENT_URI, arrayOf(CC.Contacts.LOOKUP_KEY, CC.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { c ->
            while (c.moveToNext()) m[c.getString(0)]?.let { out.add(Triple(c.getString(0), c.getString(1) ?: "?", it)) }
        }
    }
    return out.sortedBy { it.second.lowercase() }
}

/** content:// URI of the stored background (FileProvider, files-path callbg/). */
internal fun ctBgUri(ctx: Context, b: CtBg): Uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", java.io.File(b.path))

/** Copies the background into Pictures/Pulse or Movies/Pulse (MediaStore, Android 10+). */
internal fun ctBgToGallery(ctx: Context, b: CtBg): Boolean = runCatching {
    if (android.os.Build.VERSION.SDK_INT < 29) return@runCatching false
    val f = java.io.File(b.path)
    val v = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, f.name)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (b.video) "video/mp4" else "image/" + f.extension.lowercase().ifBlank { "jpeg" }.replace("jpg", "jpeg"))
        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, (if (b.video) "Movies" else "Pictures") + "/Pulse")
    }
    val base = if (b.video) android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI else android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val u = ctx.contentResolver.insert(base, v)!!
    ctx.contentResolver.openOutputStream(u)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
    true
}.getOrDefault(false)

/** Still image, or the first frame of a video. */
internal fun ctBgThumb(ctx: Context, b: CtBg): Bitmap? = runCatching {
    if (!b.video) ctPhotoBitmap(ctx, Uri.fromFile(java.io.File(b.path)))
    else android.media.MediaMetadataRetriever().let { r -> try { r.setDataSource(b.path); r.getFrameAtTime(0) } finally { r.release() } }
}.getOrNull()

// ------------------------------------------------------------------------------------------------------------ per-contact name style, photo

/** fam: 0 sans, 1 serif, 2 mono, 3 cursive, 4 condensed. size 0 = the look's default. color 0 = default. Kept by lookup key in the same prefs file as the call backgrounds (key "ctstyle"). */
class CtStyle(val fam: Int = 0, val size: Int = 0, val bold: Boolean = false, val italic: Boolean = false, val color: Int = 0)

internal fun ctStyleGet(ctx: Context, lookup: String): CtStyle = runCatching {
    val j = org.json.JSONObject(ctBgPrefs(ctx).getString("ctstyle", "{}") ?: "{}").optJSONObject(lookup) ?: return@runCatching CtStyle()
    CtStyle(j.optInt("f"), j.optInt("s"), j.optBoolean("b"), j.optBoolean("i"), j.optInt("c"))
}.getOrDefault(CtStyle())

internal fun ctStyleSet(ctx: Context, lookup: String, s: CtStyle) {
    val o = runCatching { org.json.JSONObject(ctBgPrefs(ctx).getString("ctstyle", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
    o.put(lookup, org.json.JSONObject().put("f", s.fam).put("s", s.size).put("b", s.bold).put("i", s.italic).put("c", s.color))
    ctBgPrefs(ctx).edit().putString("ctstyle", o.toString()).apply()
}

/** Centre square crop (shown as a circle), JPEG; null [bytes] removes the photo. Only the photo rows change. */
internal fun ctSetPhoto(ctx: Context, raws: List<Long>, bytes: ByteArray?): Boolean = runCatching {
    if (raws.isEmpty()) return@runCatching false
    val ops = ArrayList<ContentProviderOperation>()
    ops.add(ContentProviderOperation.newDelete(CC.Data.CONTENT_URI).withSelection("${CC.Data.RAW_CONTACT_ID} IN (${raws.joinToString(",")}) AND ${CC.Data.MIMETYPE}=?", arrayOf(Photo.CONTENT_ITEM_TYPE)).build())
    if (bytes != null) ops.add(ContentProviderOperation.newInsert(CC.Data.CONTENT_URI).withValue(CC.Data.RAW_CONTACT_ID, raws[0]).withValue(CC.Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE).withValue(Photo.PHOTO, bytes).build())
    ctx.contentResolver.applyBatch(CC.AUTHORITY, ops); true
}.getOrDefault(false)

internal fun ctSquare(b: Bitmap): Bitmap { val s = minOf(b.width, b.height); return Bitmap.createBitmap(b, (b.width - s) / 2, (b.height - s) / 2, s, s) }
