package com.umutk.pulse.extras.ai

import android.content.Context

/*
 * The looks of the chat mascot, taken from the Claude Chat app (xUmutKx/ClaudeChat): hats, body colour, face, eyes, shoes, trousers, a body extra,
 * and a scene behind it. The pictures are the Claude Chat drawables (res/drawable/cc_*). The choice is kept on the phone.
 */
internal object MascotStyle {
    val HATS = listOf("none", "wizard", "crown", "party", "bow", "cap", "phones", "halo", "ears", "santa",
        "tophat", "pirate", "chef", "cowboy", "beanie", "viking", "devil", "antlers", "bunny", "ninja", "sprout", "flower", "unicorn", "propeller", "knight", "graduate", "detective", "witch", "hardhat", "mushroom", "astronaut", "pumpkin", "fez", "tiara", "turban", "sailor",
        "beret", "fedora", "bandana", "sombrero", "mohawk", "pilot", "bucket", "headband")
    val SKINS = listOf("orange", "red", "pink", "purple", "blue", "teal", "green", "yellow", "gray", "white", "cream", "brown", "rainbow")
    val FACE = listOf("none", "glasses", "nerd", "sunglasses", "monocle", "eyepatch", "party", "mustache", "handlebar", "curly", "goatee", "beard")
    val EYES = listOf("dark", "blue", "green", "purple", "red", "gold")
    val FEET = listOf("none", "sneakers", "hightops", "boots", "socks", "sandals", "clogs", "slippers")
    val LEGS = listOf("none", "jeans", "shorts", "cargo", "overalls", "stripes", "leggings")
    val BODY = listOf("none", "cape", "scarf", "belt", "bowtie", "vest", "hoodie")
    val SCENES = listOf("none", "beach", "forest", "home", "library", "cave", "sea", "plane",
        "space", "snow", "desert", "sunset", "mountains", "city", "castle", "farm", "aurora", "volcano", "coral", "moon", "rain", "garden", "candy", "neon", "autumn", "rainbow", "meadow", "clouds")
    /** What the mascot does while it waits ("none" = it only blinks). */
    val MOVES = listOf("none", "jump", "hop", "dance", "spin", "bounce", "flip", "wobble", "sway", "backflip", "rocket")
    val SKIN_SWATCH = mapOf("orange" to 0xFFD97757, "red" to 0xFFE0524D, "pink" to 0xFFE06AA8, "purple" to 0xFF9A6AE0, "blue" to 0xFF4C8FE8, "teal" to 0xFF3FB7A8,
        "green" to 0xFF5DB85D, "yellow" to 0xFFE0B83C, "gray" to 0xFF9A9A9A, "white" to 0xFFF1F1F1, "cream" to 0xFFEBD9B8, "brown" to 0xFF8A4B36)
    private val HUE = mapOf("red" to 355f, "pink" to 330f, "purple" to 270f, "blue" to 215f, "teal" to 175f, "green" to 125f, "yellow" to 48f)

    /** One mascot's look: each slot is independent, so a hat, glasses and shoes can be worn together. */
    data class Look(val hat: String = "none", val skin: String = "orange", val face: String = "none", val eyes: String = "dark", val feet: String = "none",
                    val legs: String = "none", val body: String = "none", val scene: String = "none", val move: String = "none")

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("ai_mascot", Context.MODE_PRIVATE)

    fun load(c: Context): Look = sp(c).let { p ->
        Look(p.getString("hat", "none")!!, p.getString("skin", "orange")!!, p.getString("face", "none")!!, p.getString("eyes", "dark")!!, p.getString("feet", "none")!!,
            p.getString("legs", "none")!!, p.getString("body", "none")!!, p.getString("scene", "none")!!, p.getString("move", "none")!!)
    }

    fun save(c: Context, l: Look) {
        sp(c).edit().putString("hat", l.hat).putString("skin", l.skin).putString("face", l.face).putString("eyes", l.eyes).putString("feet", l.feet)
            .putString("legs", l.legs).putString("body", l.body).putString("scene", l.scene).putString("move", l.move).apply()
    }

    private fun res(c: Context, name: String): Int = if (name.endsWith("_none") || name.endsWith("_dark")) 0 else c.resources.getIdentifier(name, "drawable", c.packageName)

    /** The hat (or the costume piece for the four that are drawn as one), 0 for none. */
    fun hatRes(c: Context, hat: String): Int = if (hat == "none") 0 else res(c, "cc_hat_$hat").takeIf { it != 0 } ?: res(c, "cc_cos_$hat")

    /** The layers over the body, bottom to top: eye colour, trousers, shoes, the body extra, the face. */
    fun layers(c: Context, l: Look): List<Int> = listOf(res(c, "cc_eye_${l.eyes}_claude"), res(c, "cc_wear_${l.legs}_claude"), res(c, "cc_wear_${l.feet}_claude"),
        res(c, "cc_wear_${l.body}_claude"), res(c, "cc_face_${l.face}_claude")).filter { it != 0 }

    fun sceneRes(c: Context, scene: String): Int = if (scene == "none") 0 else res(c, "cc_bg_scene_$scene")

    /** 4x5 colour matrix for the body colour (null = the original orange). Rainbow: [hue] moves over time. */
    fun skinMatrix(name: String, hue: Float = 0f): FloatArray? {
        if (name == "orange") return null
        if (name == "rainbow") return hueMatrix(hue)
        if (name == "gray") return floatArrayOf(.3f, .59f, .11f, 0f, 0f,  .3f, .59f, .11f, 0f, 0f,  .3f, .59f, .11f, 0f, 0f,  0f, 0f, 0f, 1f, 0f)
        if (name == "white") return floatArrayOf(.465f, .915f, .17f, 0f, 10f,  .465f, .915f, .17f, 0f, 10f,  .465f, .915f, .17f, 0f, 10f,  0f, 0f, 0f, 1f, 0f)
        if (name == "cream") {
            val s = .35f; val g = 1.3f
            val l = floatArrayOf(.3f * (1 - s), .59f * (1 - s), .11f * (1 - s))
            val m = FloatArray(20)
            for (row in 0..2) for (k in 0..2) m[row * 5 + k] = g * (l[k] + (if (k == row) s else 0f))
            m[18] = 1f
            return m
        }
        if (name == "brown") return floatArrayOf(.62f, 0f, 0f, 0f, 0f,  0f, .62f, 0f, 0f, 0f,  0f, 0f, .62f, 0f, 0f,  0f, 0f, 0f, 1f, 0f)
        return hueMatrix(HUE[name] ?: return null)
    }

    /** Rotates the orange body to [target] degrees (orange itself sits at 15). */
    private fun hueMatrix(target: Float): FloatArray {
        val a = Math.toRadians((target - 15f).toDouble()); val c = Math.cos(a).toFloat(); val s = Math.sin(a).toFloat()
        return floatArrayOf(
            .213f + c * .787f - s * .213f, .715f - c * .715f - s * .715f, .072f - c * .072f + s * .928f, 0f, 0f,
            .213f - c * .213f + s * .143f, .715f + c * .285f + s * .140f, .072f - c * .072f - s * .283f, 0f, 0f,
            .213f - c * .213f - s * .787f, .715f - c * .715f + s * .715f, .072f + c * .928f + s * .072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
