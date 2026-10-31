package com.elcesarmaat.lockart;

import android.content.SharedPreferences;

/**
 * Ajustes del módulo. La app los escribe en las preferencias remotas de LSPosed (grupo PREFS) y el gancho de
 * SystemUI las lee y escucha sus cambios; el framework las conserva aunque la app no esté abierta.
 */
public final class Config {

    static final String PKG = "com.elcesarmaat.lockart";
    /** Permiso de firma: solo esta app puede hacerle ping al gancho de SystemUI. */
    static final String PERMISSION = PKG + ".permission.CONFIG";
    /** App -> SystemUI: pide el estado; SystemUI responde con ACTION_STATUS. */
    static final String ACTION_PING = PKG + ".PING";
    /** SystemUI -> app: estado del gancho y la carátula actual. */
    static final String ACTION_STATUS = PKG + ".STATUS";
    /** Spotify -> SystemUI: letra de una canción (con la identidad del remitente, que SystemUI comprueba). */
    static final String ACTION_LYRICS = PKG + ".LYRICS";
    /** SystemUI -> Spotify: vuelve a mandar la letra actual (solo SystemUI tiene STATUS_BAR_SERVICE). */
    static final String ACTION_LYRICS_REQUEST = PKG + ".LYRICS_REQUEST";
    static final String PREFS = "lockart";

    static final String K_ENABLED = "enabled";
    static final String K_BLUR = "blur";
    static final String K_DIM = "dim";
    static final String K_AUTO_VEIL = "auto_veil";
    static final String K_KEEP_PAUSED = "keep_paused";
    static final String K_LYRICS = "lyrics";
    static final String K_MUSIC_MODE = "music_mode";
    static final String K_AOD_LYRICS = "aod_lyrics";
    static final String K_AOD_TOGGLE = "aod_toggle";
    static final String K_LYRICS_ANIM = "lyrics_anim";
    /** SystemUI -> app: el interruptor del bloqueo cambió «Letras en pantalla ambiente»; la app lo guarda. */
    static final String ACTION_SET_AOD_LYRICS = PKG + ".SET_AOD_LYRICS";

    static final int MAX_BLUR = 25;
    static final int MAX_DIM = 80;

    boolean enabled = true;
    /** 0 = nítida; 1..MAX_BLUR = nivel de desenfoque, relativo al tamaño de la carátula (ver Blur). */
    int blur = 0;
    /** Porcentaje del velo sobre la carátula para que el reloj y las notificaciones se lean. */
    int dim = 25;
    /** Velo blanco en vez de negro cuando el fondo de bloqueo pide texto oscuro. */
    boolean autoVeil = true;
    /** Seguir mostrando la carátula con la música en pausa (Android antiguo la quitaba). */
    boolean keepPaused = false;
    /** Letras sincronizadas de Spotify en el bloqueo. */
    boolean lyrics = true;
    /** Mientras suena: sin reproductor ni notificaciones en el bloqueo y la canción bajo el reloj. */
    boolean musicMode = true;
    /** Pantalla ambiente con letras mientras suena algo con letra, aunque el AOD esté desactivado en Ajustes. */
    boolean aodLyrics = false;
    /** Interruptor discreto en el bloqueo para cambiar aodLyrics al momento. */
    boolean aodToggle = true;
    /** Animación al cambiar de línea: LyricsView.ANIM_SCROLL (de serie), ANIM_FADE, ANIM_ZOOM o ANIM_NONE. */
    int lyricsAnim = LyricsView.ANIM_SCROLL;

    void load(SharedPreferences p) {
        enabled = p.getBoolean(K_ENABLED, enabled);
        blur = clamp(p.getInt(K_BLUR, blur), 0, MAX_BLUR);
        dim = clamp(p.getInt(K_DIM, dim), 0, MAX_DIM);
        autoVeil = p.getBoolean(K_AUTO_VEIL, autoVeil);
        keepPaused = p.getBoolean(K_KEEP_PAUSED, keepPaused);
        lyrics = p.getBoolean(K_LYRICS, lyrics);
        musicMode = p.getBoolean(K_MUSIC_MODE, musicMode);
        aodLyrics = p.getBoolean(K_AOD_LYRICS, aodLyrics);
        aodToggle = p.getBoolean(K_AOD_TOGGLE, aodToggle);
        lyricsAnim = clamp(p.getInt(K_LYRICS_ANIM, lyricsAnim), LyricsView.ANIM_SCROLL, LyricsView.ANIM_NONE);
    }

    void save(SharedPreferences p) {
        p.edit()
                .putBoolean(K_ENABLED, enabled)
                .putInt(K_BLUR, blur)
                .putInt(K_DIM, dim)
                .putBoolean(K_AUTO_VEIL, autoVeil)
                .putBoolean(K_KEEP_PAUSED, keepPaused)
                .putBoolean(K_LYRICS, lyrics)
                .putBoolean(K_MUSIC_MODE, musicMode)
                .putBoolean(K_AOD_LYRICS, aodLyrics)
                .putBoolean(K_AOD_TOGGLE, aodToggle)
                .putInt(K_LYRICS_ANIM, lyricsAnim)
                .apply();
    }


    static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}
