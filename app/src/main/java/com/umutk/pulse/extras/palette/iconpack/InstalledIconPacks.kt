package com.umutk.pulse.extras.palette.iconpack

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/**
 * The icon packs installed on the phone (the standard launcher icon-pack intents), and the icon each one gives an app.
 * A pack's appfilter.xml is read from its own resources, so Palette uses the picture of an installed pack without bundling it.
 */
object InstalledIconPacks {
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME", "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme", "com.fede.launcher.THEME_ICONPACK", "com.dlto.atom.launcher.THEME", "com.gtp.nextlauncher.theme",
        "com.zeroteam.zerolauncher.theme", "com.lge.launcher2.THEME", "mobi.bbase.ahome.THEME", "com.phonemetra.turbo.launcher.THEMES",
        "home.solo.launcher.free.THEMES", "ginlemon.smartlauncher.THEMES", "com.daeva112.manager.THEME", "com.android.dxtop.launcher.THEME",
    )

    /** Each installed icon pack: its package name and its label. */
    data class Pack(val pkg: String, val label: String)

    fun list(c: Context): List<Pack> {
        val pm = c.packageManager
        return ACTIONS.flatMap { a -> runCatching { pm.queryIntentActivities(Intent(a), 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }.distinct().sorted()
            .map { p -> Pack(p, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)) }
    }
}

/** One installed pack, read once: the drawable name of each app, by its component or its package. */
class IconPack(c: Context, val pkg: String) {
    private val res: Resources = c.packageManager.getResourcesForApplication(pkg)
    private val map: Map<String, String> by lazy { readMap() }

    private fun readMap(): Map<String, String> {
        val out = HashMap<String, String>()
        val id = res.getIdentifier("appfilter", "xml", pkg)
        runCatching {
            if (id != 0) parse(res.getXml(id), out)
            else res.assets.open("appfilter.xml").use { ins ->
                val p = Xml.newPullParser(); p.setInput(ins, "utf-8"); parse(p, out)
            }
        }
        return out
    }

    private fun parse(p: XmlPullParser, out: MutableMap<String, String>) {
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "item") {
                val comp = p.getAttributeValue(null, "component")
                val draw = p.getAttributeValue(null, "drawable")
                if (comp != null && draw != null) {
                    Regex("""ComponentInfo\{([^/}]+)/([^}]+)\}""").find(comp)?.let { m ->
                        val cls = m.groupValues[2].let { if (it.startsWith(".")) m.groupValues[1] + it else it }
                        out[m.groupValues[1] + "/" + cls] = draw
                    }
                }
            }
            ev = p.next()
        }
    }

    /** Every drawable name the pack uses, for the name search below. */
    private val names: List<String> by lazy { map.values.distinct().sorted() }

    /** Every icon the pack offers (its drawable.xml, then the names of appfilter.xml), for the keyword search. */
    val allNames: List<String> by lazy {
        val out = LinkedHashSet<String>()
        runCatching {
            val id = res.getIdentifier("drawable", "xml", pkg)
            fun take(p: XmlPullParser) { var ev = p.eventType; while (ev != XmlPullParser.END_DOCUMENT) { if (ev == XmlPullParser.START_TAG && p.name == "item") p.getAttributeValue(null, "drawable")?.let { out += it }; ev = p.next() } }
            if (id != 0) take(res.getXml(id)) else res.assets.open("drawable.xml").use { ins -> val p = Xml.newPullParser(); p.setInput(ins, "utf-8"); take(p) }
        }
        out += names
        out.toList()
    }

    /** The package names each icon is drawn for (from appfilter.xml): "com.bottleice.app" finds its icon by "bottle" or "ice". */
    private val pkgsOf: Map<String, List<String>> by lazy { map.entries.groupBy({ it.value }, { it.key.substringBefore('/') }) }

    /**
     * Icons by words: each word of [query] (Turkish words are looked up in English too: "dondurma" also searches "ice", "cream", "gelato") must be found in the icon's
     * name or in the package name of an app it is drawn for. Exact name parts come first.
     */
    fun search(query: String, limit: Int = 60): List<String> {
        val words = query.lowercase().split(' ', '_', '-', '.').filter { it.length >= 2 }
        if (words.isEmpty()) return allNames.take(limit)
        val groups = words.map { w -> listOf(w) + (IconWords.en[w] ?: emptyList()) }
        fun hay(n: String) = n + " " + (pkgsOf[n]?.joinToString(" ") ?: "")
        val hits = allNames.filter { n -> val h = hay(n); groups.all { g -> g.any { h.contains(it) } } }
        val loose = if (hits.size < limit) allNames.filter { n -> n !in hits && groups.any { g -> g.any { hay(n).contains(it) } } } else emptyList()
        return (hits.sortedBy { n -> (if (groups.all { g -> g.any { n.split('_').contains(it) } }) 0 else 1) * 1000 + n.length } + loose.sortedBy { it.length }).take(limit)
    }

    /** The picture of an icon by its drawable name. */
    fun drawable(name: String): Drawable? {
        val id = res.getIdentifier(name, "drawable", pkg)
        return if (id == 0) null else runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    /**
     * The picture this pack gives the activity [activity] of [app]: its own entry first; without one, the first icon whose name is
     * the app's name (or starts with it, as a word). Names shorter than four letters are never searched ("fm", "get" match nothing).
     */
    fun iconFor(app: String, activity: String, label: String): Drawable? {
        val name = map["$app/$activity"] ?: byName(label) ?: return null
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    private fun byName(label: String): String? {
        val full = label.lowercase().filter { it.isLetterOrDigit() }
        val first = label.lowercase().trim().split(' ').firstOrNull()?.filter { it.isLetterOrDigit() }.orEmpty()
        for (key in listOf(full, first)) {
            if (key.length < 4) continue
            names.filter { it == key || it.startsWith(key + "_") }.minByOrNull { it.length }?.let { return it }
        }
        return null
    }
}

/** The packs already read, by package name. */
object IconPackCache {
    private val packs = HashMap<String, IconPack?>()
    fun get(c: Context, pkg: String): IconPack? = synchronized(packs) {
        packs.getOrPut(pkg) { runCatching { IconPack(c.applicationContext, pkg) }.getOrNull() }
    }
    fun forget() = synchronized(packs) { packs.clear() }
}

/** Turkish words and the English ones icon packs name their pictures with (and a few English synonyms). */
object IconWords {
    val en: Map<String, List<String>> = mapOf(
        "dondurma" to listOf("ice", "cream", "gelato", "popsicle", "icecream"), "buz" to listOf("ice", "frost"), "şişe" to listOf("bottle"), "sise" to listOf("bottle"),
        "kahve" to listOf("coffee", "cafe"), "çay" to listOf("tea"), "yemek" to listOf("food", "recipe", "eat", "meal"), "tarif" to listOf("recipe", "cook"), "pizza" to listOf("pizza"),
        "müzik" to listOf("music", "player", "audio"), "muzik" to listOf("music"), "şarkı" to listOf("music", "song"), "video" to listOf("video", "movie", "player"), "film" to listOf("movie", "film", "cinema"),
        "kamera" to listOf("camera"), "fotoğraf" to listOf("photo", "gallery", "picture"), "foto" to listOf("photo", "gallery"), "galeri" to listOf("gallery", "photo"),
        "hava" to listOf("weather"), "saat" to listOf("clock", "time", "watch"), "alarm" to listOf("alarm", "clock"), "takvim" to listOf("calendar"), "not" to listOf("note", "notes", "memo"), "notlar" to listOf("notes", "note"),
        "dosya" to listOf("file", "folder", "explorer"), "dosyalar" to listOf("files", "file", "folder"), "klasör" to listOf("folder"), "mesaj" to listOf("message", "sms", "chat"), "mesajlar" to listOf("messages", "sms"),
        "sohbet" to listOf("chat", "messenger"), "telefon" to listOf("phone", "dialer", "call"), "arama" to listOf("call", "dialer", "search"), "kişi" to listOf("contact", "people"), "kişiler" to listOf("contacts", "people"),
        "ayar" to listOf("settings", "setting"), "ayarlar" to listOf("settings"), "harita" to listOf("map", "maps", "navigation"), "oyun" to listOf("game", "games", "play"), "kitap" to listOf("book", "books", "reader"),
        "kitaplar" to listOf("books", "book", "library"), "hesap" to listOf("calculator", "calc", "account"), "hesapmakinesi" to listOf("calculator", "calc"), "tarayıcı" to listOf("browser", "web"),
        "indir" to listOf("download"), "indirme" to listOf("download"), "alışveriş" to listOf("shop", "shopping", "cart", "store"), "mağaza" to listOf("store", "shop", "market"), "banka" to listOf("bank", "banking"),
        "para" to listOf("money", "wallet", "cash", "pay"), "cüzdan" to listOf("wallet"), "spor" to listOf("sport", "fitness", "gym"), "sağlık" to listOf("health", "fit"), "adım" to listOf("step", "pedometer"),
        "klavye" to listOf("keyboard"), "ses" to listOf("voice", "sound", "audio", "recorder"), "kayıt" to listOf("record", "recorder"), "kaydedici" to listOf("recorder"), "çizim" to listOf("draw", "sketch", "paint"),
        "resim" to listOf("paint", "picture", "image", "draw"), "boya" to listOf("paint", "color"), "renk" to listOf("color", "palette"), "palet" to listOf("palette"), "araba" to listOf("car", "auto", "drive"),
        "uçak" to listOf("plane", "flight", "air"), "otobüs" to listOf("bus"), "tren" to listOf("train", "rail"), "taksi" to listOf("taxi", "cab"), "otel" to listOf("hotel", "booking"), "ev" to listOf("home", "house"),
        "ana" to listOf("home", "launcher"), "başlatıcı" to listOf("launcher"), "kilit" to listOf("lock", "locker"), "güvenlik" to listOf("security", "shield", "vpn"), "şifre" to listOf("password", "pass", "key"),
        "parola" to listOf("password", "pass"), "pil" to listOf("battery"), "şarj" to listOf("battery", "charge"), "temizlik" to listOf("clean", "cleaner"), "ofis" to listOf("office", "docs", "word"),
        "belge" to listOf("document", "docs", "doc", "pdf"), "pdf" to listOf("pdf", "reader"), "tablo" to listOf("sheet", "excel", "table"), "sunum" to listOf("slides", "powerpoint", "presentation"),
        "posta" to listOf("mail", "email", "gmail"), "eposta" to listOf("mail", "email"), "haber" to listOf("news"), "radyo" to listOf("radio", "fm"), "podcast" to listOf("podcast"), "çeviri" to listOf("translate"),
        "sözlük" to listOf("dictionary"), "okul" to listOf("school", "edu", "class"), "ders" to listOf("lesson", "class", "study"), "hava durumu" to listOf("weather"), "fener" to listOf("flashlight", "torch"),
        "pusula" to listOf("compass"), "tarama" to listOf("scan", "scanner"), "tarayici" to listOf("browser", "scan"), "qr" to listOf("qr", "barcode", "scan"), "yazdır" to listOf("print"), "bulut" to listOf("cloud", "drive"),
        "ice" to listOf("icecream", "cream", "gelato"), "icecream" to listOf("ice", "cream"), "cream" to listOf("ice"), "notes" to listOf("note", "memo"), "photos" to listOf("photo", "gallery"),
    )
}
