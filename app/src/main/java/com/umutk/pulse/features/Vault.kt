package com.umutk.pulse.features

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID

/** One entry of the password vault. [type] follows Bitwarden: 1 login, 2 secure note, 3 card, 4 identity, 5 SSH key. */
internal class VItem(
    val id: String = UUID.randomUUID().toString(),
    var type: Int = 1,
    var name: String = "",
    var folder: String = "",
    var notes: String = "",
    var fav: Boolean = false,
    var user: String = "",
    var pass: String = "",
    var uris: List<String> = emptyList(),
    var totp: String = "",
    /** card: holder, brand, number, expMonth, expYear, code; identity: its fields; SSH key: publicKey, privateKey, fingerprint; custom fields as "f:Name". */
    var fields: LinkedHashMap<String, String> = LinkedHashMap(),
    var updated: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("type", type).put("name", name).put("folder", folder).put("notes", notes).put("fav", fav)
        .put("user", user).put("pass", pass).put("uris", JSONArray(uris)).put("totp", totp).put("updated", updated)
        .put("fields", JSONObject().also { o -> fields.forEach { (k, v) -> o.put(k, v) } })

    companion object {
        fun fromJson(o: JSONObject): VItem {
            val f = LinkedHashMap<String, String>()
            o.optJSONObject("fields")?.let { fo -> fo.keys().forEach { k -> f[k] = fo.optString(k) } }
            val u = o.optJSONArray("uris")
            return VItem(
                o.optString("id").ifBlank { UUID.randomUUID().toString() }, o.optInt("type", 1), o.optString("name"), o.optString("folder"), o.optString("notes"), o.optBoolean("fav"),
                o.optString("user"), o.optString("pass"), if (u == null) emptyList() else List(u.length()) { u.optString(it) }, o.optString("totp"), f, o.optLong("updated"),
            )
        }
    }
}

/** What an import found, per kind. */
internal class ImportResult(val ok: Boolean, val message: String, val logins: Int = 0, val notes: Int = 0, val cards: Int = 0, val identities: Int = 0, val ssh: Int = 0, val skipped: Int = 0)

/**
 * The vault: every entry sealed with the phone's own key (the one the Authenticator uses) in a file that is private to Pulse and left out of the backups.
 * The entries stay in memory once read, so the autofill service answers at once.
 */
internal object VaultStore {
    private const val FILE = "vault.bin"
    @Volatile private var cache: List<VItem>? = null
    private fun file(c: Context) = File(c.noBackupFilesDir, FILE)

    @Synchronized fun all(c: Context): List<VItem> {
        cache?.let { return it }
        val f = file(c)
        val list = if (!f.exists()) emptyList() else runCatching {
            val arr = JSONArray(String(AuthStore.openRaw(f.readBytes()), Charsets.UTF_8))
            List(arr.length()) { VItem.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        cache = list
        return list
    }

    @Synchronized fun save(c: Context, list: List<VItem>) {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        val f = file(c)
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(AuthStore.sealRaw(arr.toString().toByteArray(Charsets.UTF_8)))
        tmp.renameTo(f)
        cache = list
    }

    fun upsert(c: Context, item: VItem) {
        item.updated = System.currentTimeMillis()
        val l = all(c)
        save(c, if (l.any { it.id == item.id }) l.map { if (it.id == item.id) item else it } else l + item)
    }

    fun delete(c: Context, id: String) = save(c, all(c).filter { it.id != id })

    /** The host of an address, lower case and without "www."; null for an app entry or text that is no address. */
    fun hostOf(uri: String): String? {
        val u = uri.trim()
        if (u.startsWith("android://") || u.startsWith("androidapp://")) return null
        val withScheme = if (u.contains("://")) u else "https://$u"
        return runCatching { URI(withScheme).host }.getOrNull()?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }

    /** The app package of an app entry ("android://hash@com.x/" or "androidapp://com.x"), else null. */
    fun packageOf(uri: String): String? {
        val u = uri.trim()
        return when {
            u.startsWith("androidapp://") -> u.removePrefix("androidapp://").trimEnd('/').takeIf { it.isNotBlank() }
            u.startsWith("android://") -> u.substringAfter('@', "").trimEnd('/').takeIf { it.isNotBlank() }
            else -> null
        }
    }

    /** The logins that fit an app package and / or a web domain the screen shows. */
    fun matches(c: Context, pkg: String?, domain: String?): List<VItem> {
        val d = domain?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        return all(c).filter { it.type == 1 && it.pass.isNotEmpty() && it.uris.any { u ->
            val p = packageOf(u)
            if (p != null) pkg != null && p == pkg
            else d != null && hostOf(u)?.let { h -> h == d || d.endsWith(".$h") || h.endsWith(".$d") } == true
        } }.sortedBy { it.name.lowercase() }
    }

    /** Reads a Bitwarden export (.json, not the encrypted kind): logins, secure notes, cards, identities and SSH keys. Entries that are already here (same id) are replaced. */
    fun importBitwarden(c: Context, text: String): ImportResult {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return ImportResult(false, "This is not a Bitwarden JSON export.")
        if (root.optBoolean("encrypted")) return ImportResult(false, "This export is encrypted. Export it again with the file type JSON (not \"encrypted\").")
        val items = root.optJSONArray("items") ?: return ImportResult(false, "No items found in the file.")
        val folders = HashMap<String, String>()
        root.optJSONArray("folders")?.let { fa -> for (i in 0 until fa.length()) fa.optJSONObject(i)?.let { folders[it.optString("id")] = it.optString("name") } }
        val old = all(c).associateBy { it.id }.toMutableMap()
        var logins = 0; var notes = 0; var cards = 0; var ids = 0; var ssh = 0; var skipped = 0
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            if (!o.isNull("deletedDate")) { skipped++; continue }
            val v = VItem(id = o.optString("id").ifBlank { UUID.randomUUID().toString() }, name = o.optString("name"), notes = if (o.isNull("notes")) "" else o.optString("notes"),
                folder = if (o.isNull("folderId")) "" else folders[o.optString("folderId")].orEmpty(), fav = o.optBoolean("favorite"))
            when (o.optInt("type")) {
                1 -> {
                    v.type = 1
                    o.optJSONObject("login")?.let { l ->
                        v.user = if (l.isNull("username")) "" else l.optString("username")
                        v.pass = if (l.isNull("password")) "" else l.optString("password")
                        v.totp = if (l.isNull("totp")) "" else l.optString("totp")
                        l.optJSONArray("uris")?.let { ua -> v.uris = (0 until ua.length()).mapNotNull { k -> ua.optJSONObject(k)?.optString("uri")?.takeIf { s -> s.isNotBlank() } } }
                    }
                    logins++
                }
                2 -> { v.type = 2; notes++ }
                3 -> { v.type = 3; copy(o.optJSONObject("card"), v.fields, mapOf("cardholderName" to "holder", "brand" to "brand", "number" to "number", "expMonth" to "expMonth", "expYear" to "expYear", "code" to "code")); cards++ }
                4 -> {
                    v.type = 4
                    val keys = listOf("title", "firstName", "middleName", "lastName", "address1", "address2", "address3", "city", "state", "postalCode", "country", "company", "email", "phone", "ssn", "username", "passportNumber", "licenseNumber")
                    copy(o.optJSONObject("identity"), v.fields, keys.associateWith { it })
                    ids++
                }
                5 -> { v.type = 5; copy(o.optJSONObject("sshKey"), v.fields, mapOf("publicKey" to "publicKey", "privateKey" to "privateKey", "keyFingerprint" to "fingerprint")); ssh++ }
                else -> { skipped++; continue }
            }
            o.optJSONArray("fields")?.let { fa -> for (k in 0 until fa.length()) fa.optJSONObject(k)?.let { f -> val n = f.optString("name"); if (n.isNotBlank() && !f.isNull("value")) v.fields["f:$n"] = f.optString("value") } }
            old[v.id] = v
        }
        save(c, old.values.toList())
        return ImportResult(true, "", logins, notes, cards, ids, ssh, skipped)
    }

    private fun copy(src: JSONObject?, into: LinkedHashMap<String, String>, map: Map<String, String>) {
        if (src == null) return
        map.forEach { (from, to) -> if (!src.isNull(from)) src.optString(from).takeIf { it.isNotEmpty() }?.let { into[to] = it } }
    }

    /** A login seen in another app's or a web page's form: saved as a new entry, or the password of the entry that has the same address and user is updated. */
    fun remember(c: Context, label: String, uri: String, user: String, pass: String) {
        if (pass.isEmpty()) return
        val l = all(c)
        val same = l.firstOrNull { it.type == 1 && it.user == user && it.uris.any { u -> u == uri || (hostOf(u) != null && hostOf(u) == hostOf(uri)) } }
        if (same != null) { if (same.pass != pass) { same.pass = pass; upsert(c, same) } }
        else upsert(c, VItem(type = 1, name = label.ifBlank { uri }, user = user, pass = pass, uris = listOf(uri)))
    }
}
