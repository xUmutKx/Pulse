package com.umutk.pulse.extras.gamehub

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/*
 * The built-in games of Game Hub: all the games of Mega Games (the first fifty with a picture of their own, the others with a made one) (the author's own collection, one HTML page with three.js; about 1 MB in the app). Each is
 * a game of the shelf like any other and opens full screen in its own window; leaving the game comes back to the shelf. One switch in the shelf's
 * settings takes them off the shelf.
 */
internal object MegaGames {
    /** A built-in game: its id inside the page, its name, and whether it is one of the big ones (they start another way). */
    class Item(val id: String, val name: String, val big: Boolean = false, val icon: String = "", val cat: String = "")

    private val CURATED = listOf(
        Item("blade", "Blade Arena", true), Item("nebula", "Nebula Voyager", true), Item("voxel", "VoxelCraft 3D", true), Item("calradia", "Calradia: Warband Conquest", true),
        Item("drift3d", "CyberDrift Highway", true), Item("spellmerge", "SpellMerge Dungeon", true), Item("fighter", "Neon Fighter 2D", true), Item("horde", "Horde Survivors", true),
        Item("bastion", "Bastion Defense", true), Item("dungeon", "Dungeon Delvers", true),
        Item("2048", "2048 Neon"), Item("tetris", "Tetris"), Item("sudoku", "Sudoku Mini"), Item("minesweeper", "Minesweeper"), Item("watersort", "Water Sort"), Item("sokoban", "Sokoban"),
        Item("checkers", "Checkers"), Item("othello", "Othello"), Item("connect4", "Connect 4"), Item("battleship", "Battleship"), Item("bubble", "Bubble Shooter"), Item("samegame", "Same Game"),
        Item("pipes", "Pipe Mania"), Item("td", "Turret Defense"), Item("village", "Pixel Village"), Item("colony", "Moon Colony"), Item("p3empire", "Empire"), Item("p3roads", "Road City"),
        Item("idle", "Idle Tycoon"), Item("p14_idle_cookie", "Cookie Factory"), Item("farm", "Cyber Farm"), Item("factory", "Factory Line"), Item("blockcraft", "Block Miner"),
        Item("snake", "Cyber Snake"), Item("breakout", "Neon Breakout"), Item("pac", "Pellet Chase"), Item("pinball", "Neon Pinball"), Item("doodle", "Doodle Jump"), Item("hillclimb", "Hill Climb"),
        Item("golf", "Mini Golf"), Item("pool", "8-Ball Pool"), Item("bowling", "Strike Bowling"), Item("racing", "Turbo Racer"), Item("drift", "Neon Drift"), Item("invaders", "Space Invaders"),
        Item("asteroid", "Asteroid Miner"), Item("tank", "Tank Blitz"), Item("p4fps", "Neon Arena FPS"), Item("p4marble", "Marble Run 3D"), Item("runner3d", "3D Metro Dash"),
    )
    /** The rest of the games of the page (every mini game of Mega Games that was not picked first): the name, the picture mark and the group the page gives each. */
    private val EXTRA = listOf(
        Item("pong", "Retro Pong", icon = "🏓", cat = "ARCADE"),
        Item("flappy", "Cyber Bird", icon = "🐤", cat = "ARCADE"),
        Item("stack", "Tower Stacker", icon = "🏗️", cat = "ARCADE"),
        Item("maze", "Neon Maze", icon = "🌀", cat = "ARCADE"),
        Item("platformer", "Cloud Platformer", icon = "🪜", cat = "ARCADE"),
        Item("runner", "Canyon Runner", icon = "🏜️", cat = "ARCADE"),
        Item("party", "Fall Dash", icon = "👑", cat = "ARCADE"),
        Item("higherlower", "Higher or Lower", icon = "🃏", cat = "ARCADE"),
        Item("rps", "Rock Paper Scissors", icon = "✂️", cat = "ARCADE"),
        Item("reaction", "Reaction Test", icon = "⚡", cat = "ARCADE"),
        Item("whack", "Whack-a-Mole", icon = "🔨", cat = "ARCADE"),
        Item("fruitcatch", "Fruit Catch", icon = "🍎", cat = "ARCADE"),
        Item("pianotiles", "Piano Tiles", icon = "🎹", cat = "ARCADE"),
        Item("dodge", "Meteor Dodge", icon = "☄️", cat = "ACTION"),
        Item("shooter", "Space Defender", icon = "🚀", cat = "ACTION"),
        Item("mech", "Mech Arena", icon = "🤖", cat = "ACTION"),
        Item("siege", "Castle Defense", icon = "🏰", cat = "ACTION"),
        Item("battleroyale", "Pixel Royale", icon = "🪂", cat = "ACTION"),
        Item("drone", "Sky Drone Strike", icon = "🛸", cat = "ACTION"),
        Item("zone", "Shrinking Zone", icon = "🎯", cat = "ACTION"),
        Item("missile", "Missile Command", icon = "🚀", cat = "ACTION"),
        Item("tron", "Tron Lightcycles", icon = "🏍️", cat = "ACTION"),
        Item("slingshot", "Sling Assault", icon = "🏹", cat = "ACTION"),
        Item("memory", "Memory Match", icon = "🃏", cat = "PUZZLE"),
        Item("ttt", "Tic-Tac-Toe AI", icon = "❌", cat = "PUZZLE"),
        Item("simon", "Simon Says", icon = "🎛️", cat = "PUZZLE"),
        Item("numberorder", "Number Order", icon = "1️⃣", cat = "PUZZLE"),
        Item("lightsout", "Lights Out", icon = "💡", cat = "PUZZLE"),
        Item("hangman", "Hangman", icon = "🔤", cat = "PUZZLE"),
        Item("endzone", "Wasteland", icon = "☢️", cat = "STRATEGY"),
        Item("basket", "Hoop Master", icon = "🏀", cat = "SPORTS"),
        Item("football", "Pixel Soccer", icon = "⚽", cat = "SPORTS"),
        Item("tennis", "Grand Tennis", icon = "🎾", cat = "SPORTS"),
        Item("darts", "Dart Master", icon = "🎯", cat = "SPORTS"),
        Item("impostor", "Crew Suspect", icon = "👾", cat = "RPG"),
        Item("space3d", "3D Warp Tunnel", icon = "🌌", cat = "3D"),
        Item("mancala", "Mancala", icon = "🕳️", cat = "PUZZLE"),
        Item("mastermind", "Code Breaker", icon = "🧠", cat = "PUZZLE"),
        Item("fifteen", "15 Puzzle", icon = "🔳", cat = "PUZZLE"),
        Item("boulder", "Boulder Dash", icon = "💎", cat = "PUZZLE"),
        Item("hanoi", "Tower of Hanoi", icon = "🗼", cat = "PUZZLE"),
        Item("pegsol", "Peg Solitaire", icon = "⚪", cat = "PUZZLE"),
        Item("shutbox", "Shut the Box", icon = "🎲", cat = "PUZZLE"),
        Item("dicepoker", "Dice Poker", icon = "🎲", cat = "PUZZLE"),
        Item("wordsearch", "Word Search", icon = "🔍", cat = "PUZZLE"),
        Item("scramble", "Word Scramble", icon = "🔤", cat = "PUZZLE"),
        Item("mathblitz", "Math Blitz", icon = "➗", cat = "PUZZLE"),
        Item("trivia", "Trivia Rush", icon = "💡", cat = "PUZZLE"),
        Item("life", "Conway Life", icon = "🦠", cat = "PUZZLE"),
        Item("sandbox", "Sandbox", icon = "⏳", cat = "PUZZLE"),
        Item("duckhunt", "Duck Hunt", icon = "🦆", cat = "ARCADE"),
        Item("frogger", "Frogger", icon = "🐸", cat = "ARCADE"),
        Item("bomber", "Bomber Maze", icon = "💣", cat = "ARCADE"),
        Item("colorswitch", "Color Switch", icon = "🎨", cat = "ARCADE"),
        Item("gravity", "Gravity Flip", icon = "🪐", cat = "ARCADE"),
        Item("nightswarm", "Night Swarm", icon = "🧛", cat = "ACTION"),
        Item("lander", "Lunar Lander", icon = "🌙", cat = "ARCADE"),
        Item("artillery", "Artillery Duel", icon = "💥", cat = "ACTION"),
        Item("tinyrts", "Tiny RTS", icon = "🏴", cat = "ACTION"),
        Item("parking", "Parking Master", icon = "🅿️", cat = "ARCADE"),
        Item("delivery", "Delivery Dash", icon = "📦", cat = "ARCADE"),
        Item("sub", "Sub Hunter", icon = "🚤", cat = "ARCADE"),
        Item("penalty", "Penalty Shootout", icon = "⚽", cat = "SPORTS"),
        Item("airhockey", "Air Hockey", icon = "🏒", cat = "SPORTS"),
        Item("homerun", "Homerun Derby", icon = "⚾", cat = "SPORTS"),
        Item("traffic", "Traffic Control", icon = "🚦", cat = "ARCADE"),
        Item("atc", "Air Traffic", icon = "🛫", cat = "ARCADE"),
        Item("plinko", "Plinko", icon = "🎯", cat = "ARCADE"),
        Item("roulette", "Roulette", icon = "🎡", cat = "ARCADE"),
        Item("blackjack", "Blackjack", icon = "🃏", cat = "ARCADE"),
        Item("stock", "Stock Trader", icon = "📈", cat = "ARCADE"),
        Item("barista", "Barista Rush", icon = "☕", cat = "ARCADE"),
        Item("fishing", "Fishing", icon = "🎣", cat = "ARCADE"),
        Item("ski", "Ski Slalom", icon = "⛷️", cat = "SPORTS"),
        Item("burger", "Burger Chef", icon = "🍔", cat = "ARCADE"),
        Item("horse", "Horse Race", icon = "🐎", cat = "SPORTS"),
    )
    private val EXTRA_DESC = mapOf(
        "pong" to "Vertical neon pong: spin, power orbs and an increasingly smart CPU.",
        "flappy" to "Tap to flap: moving pipes, shield, slow-down, perfect pass bonus.",
        "stack" to "Stack moving blocks.",
        "maze" to "Navigate random mazes.",
        "platformer" to "Bounce for gems.",
        "runner" to "Dodge boulders.",
        "party" to "Obstacle course.",
        "higherlower" to "Guess the next card.",
        "rps" to "Beat adaptive AI.",
        "reaction" to "Tap when green.",
        "whack" to "Whack the moles.",
        "fruitcatch" to "Catch fruit, dodge bombs.",
        "pianotiles" to "Hit falling notes.",
        "dodge" to "Dodge meteors.",
        "shooter" to "Destroy alien waves.",
        "mech" to "Heat & energy.",
        "siege" to "Hold the wall.",
        "battleroyale" to "Eliminate rivals.",
        "drone" to "Drone shooter.",
        "zone" to "Collect orbs.",
        "missile" to "Homing missiles.",
        "tron" to "Outlive riders.",
        "slingshot" to "Demolish the wall.",
        "memory" to "Match card pairs.",
        "ttt" to "Beat the AI.",
        "simon" to "Repeat sequence.",
        "numberorder" to "Tap 1–12 in order.",
        "lightsout" to "All lights off.",
        "hangman" to "Guess the word.",
        "endzone" to "Survive fallout.",
        "basket" to "Score baskets.",
        "football" to "Shoot goals.",
        "tennis" to "Fast rallies.",
        "darts" to "Hit the bullseye.",
        "impostor" to "Tasks & suspects.",
        "space3d" to "Pseudo-3D tunnel.",
        "mancala" to "Sow & capture.",
        "mastermind" to "Crack the code.",
        "fifteen" to "Slide into order.",
        "boulder" to "Grab diamonds.",
        "hanoi" to "Move the tower.",
        "pegsol" to "One peg left.",
        "shutbox" to "Close all numbers.",
        "dicepoker" to "Poker hands.",
        "wordsearch" to "Step on letters.",
        "scramble" to "Rebuild words.",
        "mathblitz" to "Solve equations.",
        "trivia" to "Answer quizzes.",
        "life" to "Grow cells.",
        "sandbox" to "Paint falling sand.",
        "duckhunt" to "Shoot the ducks.",
        "frogger" to "Cross the road.",
        "bomber" to "Blast crates.",
        "colorswitch" to "Match the ring.",
        "gravity" to "Flip gravity.",
        "nightswarm" to "Auto-fire survival.",
        "lander" to "Soft landings.",
        "artillery" to "Aim & charge.",
        "tinyrts" to "Spawn units.",
        "parking" to "Maneuver in.",
        "delivery" to "Pick & drop.",
        "sub" to "Torpedo ships.",
        "penalty" to "Beat the keeper.",
        "airhockey" to "First to 3.",
        "homerun" to "Swing timing.",
        "traffic" to "Toggle lights.",
        "atc" to "Clear planes.",
        "plinko" to "Drop into slots.",
        "roulette" to "Red or black.",
        "blackjack" to "Beat the dealer.",
        "stock" to "Buy low sell high.",
        "barista" to "Serve orders.",
        "fishing" to "Cast & reel.",
        "ski" to "Race the gates.",
        "burger" to "Stack the order.",
        "horse" to "Bet on horses.",
    )

    /** Every game of the page: the first fifty, then all the others, so the shelf shows the whole collection. */
    val LIST: List<Item> = CURATED + EXTRA.filter { e -> CURATED.none { it.id == e.id } }
    const val PREFIX = "mega:"

    /** What each game is, in a line: English, Turkish (shown on the game's Details). */
    private val DESC = mapOf(
        "blade" to ("Sword duels against waves of fighters in a 3D arena." to "Üç boyutlu arenada dalga dalga savaşçılara karşı kılıç düellosu."),
        "nebula" to ("Fly a ship through a nebula: shoot and dodge." to "Bir nebulanın içinde gemi uçur: vur ve kaç."),
        "voxel" to ("Mine and build with blocks in a 3D world." to "Üç boyutlu dünyada blok kazı ve inşa et."),
        "calradia" to ("A medieval war game: lead an army and conquer lands." to "Orta çağ savaş oyunu: ordu yönet, toprak fethet."),
        "drift3d" to ("Race and drift on a neon highway at night." to "Gece neon otobanda yarış ve drift yap."),
        "spellmerge" to ("Merge spells to fight your way through a dungeon." to "Büyüleri birleştirerek zindanda ilerle."),
        "fighter" to ("A 2D fighting game with neon fighters." to "Neon dövüşçülerle 2D dövüş oyunu."),
        "horde" to ("Survive waves of monsters and grow stronger." to "Canavar dalgalarına karşı hayatta kal, güçlen."),
        "bastion" to ("Defend your fortress with towers and units." to "Kuleler ve birliklerle kaleni savun."),
        "dungeon" to ("Explore dungeons, fight and collect loot." to "Zindanları keşfet, savaş, ganimet topla."),
        "2048" to ("Slide and merge tiles until you reach 2048." to "Kareleri kaydırıp birleştir, 2048'e ulaş."),
        "tetris" to ("Stack falling blocks and clear lines." to "Düşen blokları diz, satırları temizle."),
        "sudoku" to ("Fill the 4×4 grid with no repeats." to "4×4 tabloyu tekrarsız doldur."),
        "minesweeper" to ("Open safe cells and avoid the mines." to "Güvenli kareleri aç, mayınlardan kaç."),
        "watersort" to ("Pour water until every tube holds one colour." to "Her tüpte tek renk kalana dek suyu aktar."),
        "sokoban" to ("Push crates onto the marks." to "Kasaları işaretli yerlere it."),
        "checkers" to ("Capture all of the other player's pieces." to "Rakibin tüm taşlarını ye."),
        "othello" to ("Flip the other colour by surrounding it." to "Rakibin rengini kuşatarak çevir."),
        "connect4" to ("Line up four discs before your rival." to "Rakibinden önce dört taşı yan yana getir."),
        "battleship" to ("Guess where the enemy ships are and sink them." to "Düşman gemilerinin yerini tahmin edip batır."),
        "bubble" to ("Shoot bubbles and pop groups of three or more." to "Balon at, üç ve fazlasını patlat."),
        "samegame" to ("Pop groups of same-coloured blocks." to "Aynı renkli blok gruplarını patlat."),
        "pipes" to ("Connect the pipes before the water flows." to "Su akmadan boruları birleştir."),
        "td" to ("Build turrets to stop the waves." to "Dalgaları durdurmak için taret kur."),
        "village" to ("Build a pixel village and keep it fed." to "Piksel köyü kur, halkı doyur."),
        "colony" to ("Build a colony on the moon." to "Ayda koloni kur."),
        "p3empire" to ("An idle empire: build, ascend, collect crowns." to "Boşta imparatorluk: kur, yüksel, taç topla."),
        "p3roads" to ("Draw roads from houses to their targets; do not let traffic jam." to "Evlerden hedeflere yol çiz; trafiğin tıkanmasına izin verme."),
        "idle" to ("Tap and upgrade your business." to "Dokun ve işini geliştir."),
        "p14_idle_cookie" to ("Bake cookies, buy ovens and grow the factory." to "Kurabiye yap, fırın al, fabrikayı büyüt."),
        "farm" to ("Plant, harvest and sell." to "Ek, hasat et, sat."),
        "factory" to ("Move goods through a production line." to "Malları üretim hattından geçir."),
        "blockcraft" to ("Mine blocks and build." to "Blok kaz ve bir şeyler inşa et."),
        "snake" to ("Eat apples and grow without hitting yourself." to "Elma ye, kendine çarpmadan büyü."),
        "breakout" to ("Break the bricks with a bouncing ball." to "Zıplayan topla tuğlaları kır."),
        "pac" to ("Eat every pellet and keep away from the ghosts." to "Tüm topları ye, hayaletlerden kaç."),
        "pinball" to ("Flip the ball, hit bumpers, chase a high score." to "Topu çevir, tamponlara vur, yüksek skorun peşine düş."),
        "doodle" to ("Jump upward from platform to platform." to "Platformdan platforma yukarı zıpla."),
        "hillclimb" to ("Drive up the hills and watch your fuel." to "Tepeleri tırman, yakıta dikkat et."),
        "golf" to ("Putt the ball into the hole in the fewest strokes." to "Topu en az vuruşta deliğe sok."),
        "pool" to ("Pocket your balls in 8-ball pool." to "8 top bilardoda toplarını cebe sok."),
        "bowling" to ("Roll the ball and knock down all the pins." to "Topu yuvarla, tüm lobutları devir."),
        "racing" to ("Race three laps and reach the podium." to "Üç tur yarış, podyuma çık."),
        "drift" to ("Chain drifts for points." to "Drift'leri zincirle, puan topla."),
        "invaders" to ("Shoot the descending rows of aliens." to "İnen uzaylı sıralarını vur."),
        "asteroid" to ("Shoot asteroids and mine them." to "Asteroitleri vur ve kaz."),
        "tank" to ("Fight enemy tanks." to "Düşman tanklarıyla savaş."),
        "p4fps" to ("A fast first-person shooter in a neon arena." to "Neon arenada hızlı birinci şahıs nişancı."),
        "p4marble" to ("Guide a marble through a 3D track." to "Bir bilyeyi 3D parkurdan geçir."),
        "runner3d" to ("Run through a 3D city and dodge the obstacles." to "3D şehirde engellerden kaçarak koş."),
    )

    /** What the game is, in the phone's language. */
    fun desc(pkg: String): String = DESC[pkg.removePrefix(PREFIX)]?.let { com.umutk.pulse.tr(it.first, it.second) } ?: EXTRA_DESC[pkg.removePrefix(PREFIX)].orEmpty()

    /** Built-in games taken off the shelf by hand (the shelf's back cover), until they are brought back in the settings. */
    fun removed(c: Context): Set<String> = prefs(c).getStringSet("megaRemoved", emptySet()) ?: emptySet()
    fun remove(c: Context, pkg: String) { prefs(c).edit().putStringSet("megaRemoved", removed(c) + pkg.removePrefix(PREFIX)).apply() }
    fun restoreAll(c: Context) { prefs(c).edit().remove("megaRemoved").apply() }

    /** On the shelf unless switched off in the shelf's settings. */
    fun enabled(c: Context): Boolean = prefs(c).getBoolean("miniGames", true)

    fun isMega(pkg: String) = pkg.startsWith(PREFIX)

    /** The picture of a game of the page: its own (assets/megacovers), or for the others one made from its mark and name on a colour, kept in the cache. Null for an unknown id. */
    fun cover(c: Context, id: String): android.graphics.Bitmap? {
        runCatching { c.assets.open("megacovers/$id.webp").use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()?.let { return it }
        val f = java.io.File(c.cacheDir, "megacover/$id.png")
        if (f.isFile) android.graphics.BitmapFactory.decodeFile(f.path)?.let { return it }
        val item = LIST.firstOrNull { it.id == id } ?: return null
        val b = made(item)
        runCatching { f.parentFile?.mkdirs(); f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        return b
    }

    /** A 400 x 600 picture: a colour of its own (from its name), the game's mark large in the upper part, its group above and its name below. */
    private fun made(item: Item): android.graphics.Bitmap {
        val w = 400; val h = 600
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val hue = (kotlin.math.abs(item.id.hashCode()) % 360).toFloat()
        val top = android.graphics.Color.HSVToColor(floatArrayOf(hue, .62f, .85f)); val bottom = android.graphics.Color.HSVToColor(floatArrayOf((hue + 24f) % 360f, .75f, .30f))
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, android.graphics.Shader.TileMode.CLAMP) })
        cv.drawCircle(w * .5f, 250f, 150f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
        if (item.icon.isNotEmpty()) cv.drawText(item.icon, w / 2f, 250f + 70f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 190f; textAlign = android.graphics.Paint.Align.CENTER })
        if (item.cat.isNotEmpty()) cv.drawText(item.cat, w / 2f, 62f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = 26f; letterSpacing = .2f; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true })
        val tp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 46f; isFakeBoldText = true; setShadowLayer(8f, 0f, 3f, 0xAA000000.toInt()) }
        val lay = android.text.StaticLayout.Builder.obtain(item.name, 0, item.name.length, tp, w - 60).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        cv.save(); cv.translate(30f, 440f); lay.draw(cv); cv.restore()
        return bmp
    }

    fun open(c: Context, pkg: String) {
        val it = LIST.firstOrNull { PREFIX + it.id == pkg } ?: return
        runCatching { c.startActivity(Intent(c, MegaGameActivity::class.java).putExtra("id", it.id).putExtra("big", it.big).apply { if (c !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
    }
}

/** One built-in game, full screen. The page is the whole collection; the chosen game is started in it, and the page's own list is never shown. */
class MegaGameActivity : Activity() {
    private var web: WebView? = null
    private var started = false
    private val host = "mega.pulse.local"

    inner class Bridge {
        /** The page went back to its own list (the game was left): this window closes, the shelf is under it. */
        @JavascriptInterface fun hubShown() { runOnUiThread { if (started) finish() } }
        @JavascriptInterface fun vibrate(ms: Int) {
            runCatching {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                val d = ms.coerceIn(1, 2000).toLong()
                if (android.os.Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createOneShot(d, android.os.VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(d)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getStringExtra("id") ?: run { finish(); return }
        val big = intent.getBooleanExtra("big", false)
        val w = WebView(this)
        web = w
        w.setBackgroundColor(Color.BLACK)
        w.alpha = 0f
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.settings.textZoom = 100
        w.settings.allowFileAccess = false
        w.isLongClickable = false
        w.setOnLongClickListener { true }
        w.addJavascriptInterface(Bridge(), "AndroidBridge")
        w.webViewClient = object : WebViewClient() {
            // the page and three.js come from the app itself, under a name of their own (so the games keep their saves between starts)
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                if (u.host != host) return null
                val name = u.lastPathSegment ?: return null
                if (name != "mega.html" && name != "three.min.js") return WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                return runCatching { WebResourceResponse(if (name.endsWith(".js")) "application/javascript" else "text/html", "utf-8", assets.open("mega/$name")) }.getOrNull()
            }
            override fun onPageFinished(view: WebView, url: String?) { launch(id, big, 0) }
        }
        setContentView(w, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        immersive()
        w.loadUrl("https://$host/mega.html")
    }

    /** Starts the game once the page has its games ready (asked again a few times while it loads). */
    private fun launch(id: String, big: Boolean, n: Int) {
        val w = web ?: return
        if (started || n > 60) return
        val q = JSONObject.quote(id)
        val js = "(function(){try{if(typeof MINIS==='undefined'||typeof launchAAA!=='function')return 'wait';" +
            (if (big) "launchAAA($q);" else "var g=MINIS.find(function(m){return m.id===$q});if(!g)return 'none';hubOpen(g);") + "return 'ok'}catch(e){return 'err'}})()"
        w.evaluateJavascript(js) { r ->
            when {
                r != null && r.contains("ok") -> { w.postDelayed({ started = true; w.animate().alpha(1f).setDuration(150).start() }, 80) }
                r != null && r.contains("wait") -> w.postDelayed({ launch(id, big, n + 1) }, 150)
                else -> finish()
            }
        }
    }

    private fun immersive() {
        runCatching {
            val c = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }
    override fun onPause() { super.onPause(); web?.onPause() }
    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onDestroy() { web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; web = null; super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back leaves the game (the page closes it, which closes this window); when the page does not answer the window closes itself
        val w = web ?: run { finish(); return }
        w.evaluateJavascript("(function(){try{var h=document.getElementById('hubScreen');if(h&&h.classList.contains('hidden')&&typeof goHub==='function'){goHub();return 'back'}}catch(e){}return 'none'})()") { finish() }
    }
}
