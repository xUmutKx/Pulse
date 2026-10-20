package com.umutk.pulse.extras.notes

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.security.MessageDigest
import java.security.SecureRandom

/** The notes app's own settings. Never Pulse's Cfg or Opt. */
object NotesPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

    enum class Sort(val en: String, val tr: String) { UPDATED("Last edited", "Son düzenleme"), CREATED("Created", "Oluşturulma"), TITLE("Title", "Başlık") }

    /** Paper colours for the editor surface; AUTO keeps the look's own panel colour. */
    data class Paper(val id: String, val en: String, val tr: String, val bg: Long, val ink: Long)
    val papers = listOf(
        Paper("auto", "Look", "Tema", 0, 0),
        Paper("cream", "Cream", "Krem", 0xFFFFF8E1, 0xFF3E2723),
        Paper("sepia", "Sepia", "Sepya", 0xFFF4E4C1, 0xFF4E342E),
        Paper("mint", "Mint", "Nane", 0xFFE0F2F1, 0xFF004D40),
        Paper("slate", "Slate", "Arduvaz", 0xFF263238, 0xFFECEFF1),
        Paper("night", "Night", "Gece", 0xFF000000, 0xFFE0E0E0),
    )

    /** Note colours: 0 is none; the others are the label colours. */
    val colors = listOf(0, 0xFFE57373.toInt(), 0xFFFFB74D.toInt(), 0xFFFFF176.toInt(), 0xFF81C784.toInt(), 0xFF64B5F6.toInt(), 0xFFBA68C8.toInt())

    fun sort(c: Context): Sort = Sort.values().firstOrNull { it.name == sp(c).getString("sort", null) } ?: Sort.UPDATED
    fun setSort(c: Context, s: Sort) = sp(c).edit().putString("sort", s.name).apply()

    fun reverse(c: Context) = sp(c).getBoolean("reverse", false)
    fun setReverse(c: Context, on: Boolean) = sp(c).edit().putBoolean("reverse", on).apply()

    fun grid(c: Context) = sp(c).getBoolean("grid", false)
    fun setGrid(c: Context, on: Boolean) = sp(c).edit().putBoolean("grid", on).apply()

    fun groups(c: Context) = sp(c).getBoolean("groups", true)
    fun setGroups(c: Context, on: Boolean) = sp(c).edit().putBoolean("groups", on).apply()

    fun previews(c: Context) = sp(c).getBoolean("previews", true)
    fun setPreviews(c: Context, on: Boolean) = sp(c).edit().putBoolean("previews", on).apply()

    fun dates(c: Context) = sp(c).getBoolean("dates", true)
    fun setDates(c: Context, on: Boolean) = sp(c).edit().putBoolean("dates", on).apply()

    /** Text size of the editor and list previews, in sp. */
    fun fontSize(c: Context) = sp(c).getInt("font", 16)
    fun setFontSize(c: Context, sp: Int) = sp(c).edit().putInt("font", sp).apply()

    fun defaultColor(c: Context) = sp(c).getInt("defaultColor", 0)
    fun setDefaultColor(c: Context, argb: Int) = sp(c).edit().putInt("defaultColor", argb).apply()

    fun paper(c: Context): Paper = papers.firstOrNull { it.id == sp(c).getString("paper", "auto") } ?: papers[0]
    fun setPaper(c: Context, id: String) = sp(c).edit().putString("paper", id).apply()

    /** Keeps the screen out of screenshots and recents. */
    fun secure(c: Context) = sp(c).getBoolean("secure", false)
    fun setSecure(c: Context, on: Boolean) = sp(c).edit().putBoolean("secure", on).apply()

    /** The notes file is AES-GCM encrypted with a Keystore key. */
    fun encrypted(c: Context) = sp(c).getBoolean("encrypted", false)
    fun setEncrypted(c: Context, on: Boolean) = sp(c).edit().putBoolean("encrypted", on).apply()

    fun hasPin(c: Context) = sp(c).contains("pinHash")
    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp(c).edit().putString("pinSalt", salt.joinToString("") { "%02x".format(it) }).putString("pinHash", hash(pin, salt)).apply()
    }
    fun checkPin(c: Context, pin: String): Boolean {
        val salt = sp(c).getString("pinSalt", null) ?: return false
        val bytes = ByteArray(salt.length / 2) { salt.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return hash(pin, bytes) == sp(c).getString("pinHash", null)
    }
    fun clearPin(c: Context) = sp(c).edit().remove("pinSalt").remove("pinHash").apply()

    private fun hash(pin: String, salt: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Locked notes shown in this session; cleared when the editor closes the locked note. */
    val unlocked = HashSet<String>()

    /** A note colour as a Compose colour; 0 is no colour. */
    fun argb(v: Int): Color = if (v == 0) Color.Transparent else Color(v)
}
