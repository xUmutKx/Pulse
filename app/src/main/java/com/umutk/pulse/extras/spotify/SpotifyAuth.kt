package com.umutk.pulse.extras.spotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Spotify login: official Authorization Code flow with PKCE, no client secret.
 * The user pastes their own client id (developer.spotify.com dashboard, redirect URI [REDIRECT]); [DEFAULT_CLIENT_ID] is only a placeholder.
 */
object SpotifyAuth {
    const val DEFAULT_CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID"
    const val REDIRECT = "pulse-spotify://callback"
    private const val SCOPES = "user-read-private playlist-read-private playlist-read-collaborative user-library-read user-read-playback-state user-modify-playback-state user-read-currently-playing"

    var loggedIn by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("spotify", Context.MODE_PRIVATE)

    fun clientId(c: Context): String = sp(c).getString("client_id", null)?.trim().orEmpty().ifEmpty { DEFAULT_CLIENT_ID }
    fun setClientId(c: Context, id: String) { sp(c).edit().putString("client_id", id.trim()).apply() }
    fun hasClientId(c: Context) = clientId(c) != DEFAULT_CLIENT_ID

    fun restore(c: Context) { loggedIn = sp(c).getString("refresh", null) != null }

    fun logout(c: Context) { sp(c).edit().remove("access").remove("refresh").remove("expires").apply(); loggedIn = false }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Opens the Spotify login page in the browser. */
    fun login(c: Context) {
        val verifier = b64(ByteArray(48).also { SecureRandom().nextBytes(it) })
        val state = b64(ByteArray(12).also { SecureRandom().nextBytes(it) })
        sp(c).edit().putString("verifier", verifier).putString("state", state).apply()
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val url = "https://accounts.spotify.com/authorize?client_id=${enc(clientId(c))}&response_type=code&redirect_uri=${enc(REDIRECT)}" +
            "&code_challenge_method=S256&code_challenge=$challenge&scope=${enc(SCOPES)}&state=$state"
        c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Called by [SpotifyAuthActivity] with the redirect. */
    suspend fun finish(c: Context, data: Uri) {
        val s = sp(c)
        val code = data.getQueryParameter("code")
        if (code == null || data.getQueryParameter("state") != s.getString("state", null)) { error = data.getQueryParameter("error") ?: "login"; return }
        try {
            store(c, post(c, "grant_type=authorization_code&code=${enc(code)}&redirect_uri=${enc(REDIRECT)}&client_id=${enc(clientId(c))}&code_verifier=${enc(s.getString("verifier", "").orEmpty())}"))
            error = null
        } catch (e: Exception) { error = e.message ?: "login" }
    }

    private suspend fun post(c: Context, form: String): JSONObject = withContext(Dispatchers.IO) {
        val h = URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection
        try {
            h.requestMethod = "POST"; h.doOutput = true; h.connectTimeout = 10_000; h.readTimeout = 15_000
            h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            h.outputStream.use { it.write(form.toByteArray()) }
            val body = (if (h.responseCode in 200..299) h.inputStream else h.errorStream).bufferedReader().use { it.readText() }
            if (h.responseCode !in 200..299) throw java.io.IOException("Spotify ${h.responseCode}")
            JSONObject(body)
        } finally { h.disconnect() }
    }

    private fun store(c: Context, o: JSONObject) {
        val e = sp(c).edit().putString("access", o.getString("access_token"))
            .putLong("expires", System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000 - 60_000)
        o.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { e.putString("refresh", it) }
        e.apply(); loggedIn = true
    }

    /** A valid access token, refreshed when needed; null (and logged out) when the refresh fails for good. */
    suspend fun token(c: Context): String? {
        val s = sp(c)
        val t = s.getString("access", null)
        if (t != null && System.currentTimeMillis() < s.getLong("expires", 0)) return t
        val r = s.getString("refresh", null) ?: return null
        return try {
            store(c, post(c, "grant_type=refresh_token&refresh_token=${enc(r)}&client_id=${enc(clientId(c))}"))
            s.getString("access", null)
        } catch (e: java.io.IOException) { if (e.message?.contains("400") == true) logout(c); null }
    }
}

/** Receives pulse-spotify://callback, finishes the login, then returns to the app. */
class SpotifyAuthActivity : Activity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); handle(intent) }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); handle(i) }

    private fun handle(i: Intent) {
        val d = i.data ?: return finish()
        val app = applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            SpotifyAuth.finish(app, d)
            packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)?.let { startActivity(it) }
            finish()
        }
    }
}
