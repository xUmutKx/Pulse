package com.elcesarmaat.lockart;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/**
 * Vive en el proceso de SystemUI. Sigue las sesiones multimedia con MediaSessionManager (SystemUI tiene
 * MEDIA_CONTENT_CONTROL) y pinta la carátula en una vista a pantalla completa debajo del contenido del bloqueo.
 *
 * Dos alfas independientes:
 * - container: sigue al bloqueo, interpolado con cada paso de KeyguardTransitionRepository (ver Hook).
 * - stage: aparece/desaparece con la música y con el ajuste "Mostrar carátula".
 */
final class ArtController {

    private static final String TAG = "LockArt";
    private static final long FADE_MS = 350;
    private static final long CROSSFADE_MS = 450;
    /** Margen antes de quitar la carátula: algunas apps pasan un instante por PAUSED o NONE al cambiar de pista. */
    private static final long CLEAR_DELAY_MS = 1200;
    /** Tras un ping de la app, SystemUI le avisa de cada cambio de carátula durante este tiempo. */
    private static final long STATUS_WINDOW_MS = 10 * 60 * 1000L;
    private static final int MAX_ART_PX = 1440;
    private static final int THUMB_PX = 480;

    /**
     * Estados del bloqueo con carátula. Con Scene Container solo existen OFF, DOZING, AOD, ALTERNATE_BOUNCER,
     * LOCKSCREEN y UNDEFINED (desbloqueado, bouncer, panel, ocluido, sueño...); PRIMARY_BOUNCER queda para Android
     * sin Scene Container.
     */
    private static final Set<String> SHOWN = new HashSet<>(Arrays.asList("LOCKSCREEN", "ALTERNATE_BOUNCER", "PRIMARY_BOUNCER"));

    private static final String[] BITMAP_KEYS = {
            MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON};
    private static final String[] URI_KEYS = {
            MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI};

    private static ArtController instance;

    static synchronized ArtController get() {
        if (instance == null) instance = new ArtController();
        return instance;
    }

    private static XposedInterface logger;

    static void setLogger(XposedInterface xposed) {
        logger = xposed;
    }

    /** Al log de LSPosed (y logcat) cuando el módulo está cargado; solo a logcat si no. */
    static void log(String msg) {
        if (logger != null) logger.log(Log.INFO, TAG, msg, null);
        else Log.i(TAG, msg);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else MAIN.post(r);
    }

    private final Handler main = MAIN;
    private final Config cfg = new Config();
    private Handler worker;
    private HandlerThread workerThread;
    private Context ctx;
    /** Preferencias remotas de LSPosed: las escribe la app y aquí son de solo lectura. */
    private SharedPreferences prefs;
    private WallpaperManager wallpaper;
    private MediaSessionManager sessions;
    private BroadcastReceiver pingReceiver;
    private String hookInfo = "";

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (p, key) -> runOnMain(this::onPrefsChanged);
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::onSessionsChanged;
    private final WallpaperManager.OnColorsChangedListener colorsListener = (colors, which) -> updateVeilColor();

    private ViewGroup root;
    private FrameLayout container;
    private FrameLayout stage;
    private ImageView back;
    private ImageView front;
    private View veil;
    private boolean stageShown;

    private float lockAlpha;
    private float lyricsAlpha;
    private AodToggleView aodToggle;
    private boolean toggleEligible;
    private String lockState = "OFF";

    private final List<MediaController> controllers = new ArrayList<>();

    // Letras (de Spotify, vía SpotifyLyrics en su proceso)
    private LyricsView lyricsView;
    private BroadcastReceiver lyricsReceiver;
    private final Map<String, Lyrics> lyricsByTrack = new LinkedHashMap<String, Lyrics>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Lyrics> e) {
            return size() > 6;
        }
    };
    private final Set<String> noLyrics = new HashSet<>();
    /** Sesión elegida y su último estado: la posición de las letras se calcula sin llamadas binder. */
    private MediaController active;
    private PlaybackState activeState;
    private String activeTrack;
    private String requestedLyrics;
    private final MusicMode musicMode = new MusicMode();
    /** Enciende el AOD con letras solo si se quiere y el teléfono no está en el bolsillo ni boca abajo. */
    private final AodGuard aodGuard = new AodGuard(on -> musicMode.setForceAod(on));
    private String requestedKey;
    private int generation;
    private boolean clearPending;
    private Bitmap source;
    private Bitmap shown;
    private String pkg;
    private String title;
    private String artist;
    private boolean veilLight;
    private long statusUntil;
    /** Solo en el hilo de trabajo: la carátula más grande vista de la canción actual. */
    private String bestTrack;
    private Bitmap bestArt;

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            scheduleRefresh();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            scheduleRefresh();
        }

        @Override
        public void onSessionDestroyed() {
            scheduleRefresh();
        }
    };

    private final Runnable refreshRunnable = this::refresh;

    private final Runnable clearRunnable = () -> {
        clearPending = false;
        requestedKey = null;
        generation++;
        setArt(null, null, 0, null, null, null);
    };

    private ArtController() {
    }

    MusicMode musicMode() {
        return musicMode;
    }

    void setHookInfo(String info) {
        hookInfo = info;
    }

    String hookInfo() {
        return hookInfo;
    }

    // ---- Vista ----

    /**
     * Se llama desde onAttachedToWindow, mientras la raíz recorre sus hijos: tocar la jerarquía ahí desplazaría ese
     * recorrido, así que se inserta en el siguiente ciclo del hilo principal.
     */
    void attach(View rootView) {
        if (!(rootView instanceof ViewGroup)) return;
        final ViewGroup r = (ViewGroup) rootView;
        main.post(() -> insertInto(r));
    }

    private void insertInto(ViewGroup r) {
        try {
            if (ctx == null) init(r.getContext());
            if (container == null) build(r.getContext());
            keepLyricsOnTop(r);
            if (container.getParent() == r && r.indexOfChild(container) == 0) return;
            if (container.getParent() instanceof ViewGroup) ((ViewGroup) container.getParent()).removeView(container);
            r.addView(container, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            root = r;
            log("carátula insertada en " + r.getClass().getName() + " (" + r.getChildCount() + " hijos)");
        } catch (Throwable t) {
            log("no se pudo insertar la carátula: " + Log.getStackTraceString(t));
        }
    }

    /**
     * Las letras van en una capa propia encima de la escena Compose: así no las tapan las notificaciones y se ven en
     * AOD, donde la escena pinta negro sobre lo que hay debajo. No interceptan toques (no son clicables).
     */
    private void keepLyricsOnTop(ViewGroup r) {
        if (lyricsView == null) return;
        onTop(r, lyricsView);
        onTop(r, aodToggle);
    }

    private static void onTop(ViewGroup r, View v) {
        if (v == null) return;
        if (v.getParent() != r) {
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            r.addView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (r.indexOfChild(v) != r.getChildCount() - 1) {
            v.bringToFront();
        }
    }

    private void build(Context c) {
        container = new FrameLayout(c);
        container.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        container.setAlpha(lockAlpha);
        container.setVisibility(lockAlpha > 0f ? View.VISIBLE : View.INVISIBLE);

        stage = new FrameLayout(c);
        stage.setAlpha(0f);
        stage.setVisibility(View.INVISIBLE);
        back = image(c);
        front = image(c);
        front.setVisibility(View.INVISIBLE);
        veil = new View(c);
        stage.addView(back, matchParent());
        stage.addView(front, matchParent());
        stage.addView(veil, matchParent());
        container.addView(stage, matchParent());
        lyricsView = new LyricsView(c, new LyricsView.Clock() {
            @Override
            public long position() {
                return positionNow();
            }

            @Override
            public boolean playing() {
                return activeState != null && activeState.getState() == PlaybackState.STATE_PLAYING;
            }
        });
        aodToggle = new AodToggleView(c, this::onAodToggle);
        aodToggle.setOn(cfg.aodLyrics);
        refreshToggle();
        lyricsView.setAlpha(lyricsAlpha);
        lyricsView.setVisibility(lyricsAlpha > 0.001f ? View.VISIBLE : View.INVISIBLE);

        applyVeil();
        if (shown != null) back.setImageBitmap(shown);
        updateStage(false);
    }

    private static ImageView image(Context c) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ---- Bloqueo ----

    /** Un paso de KeyguardTransitionRepository: el alfa se interpola entre los dos estados según el progreso. */
    void onKeyguardStep(final String from, final String to, final float value, final String state) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> onKeyguardStep(from, to, value, state));
            return;
        }
        if (!"RUNNING".equals(state)) Log.d(TAG, state + " " + from + " -> " + to + " (" + value + ")");
        if ("CANCELED".equals(state)) return;
        float a0 = SHOWN.contains(from) ? 1f : 0f;
        float a1 = SHOWN.contains(to) ? 1f : 0f;
        float v = value < 0f ? 0f : (value > 1f ? 1f : value);
        lockState = "FINISHED".equals(state) ? to : from + " -> " + to;
        setLockAlpha(a0 + (a1 - a0) * v);
        float l0 = lyricsVisibleIn(from), l1 = lyricsVisibleIn(to);
        float m0 = "AOD".equals(from) ? 1f : 0f, m1 = "AOD".equals(to) ? 1f : 0f;
        if ("STARTED".equals(state) && root != null) keepLyricsOnTop(root);
        if ("FINISHED".equals(state)) aodGuard.setDozing("AOD".equals(to) || "DOZING".equals(to));
        setLyricsAlpha(l0 + (l1 - l0) * v, m0 + (m1 - m0) * v);
        if ("FINISHED".equals(state)) pushStatus();
    }

    /** Letras visibles en el bloqueo y, con «Letras en pantalla ambiente» forzada, también en AOD. */
    private float lyricsVisibleIn(String s) {
        if (SHOWN.contains(s)) return 1f;
        return "AOD".equals(s) && musicMode.isAodForced() ? 1f : 0f;
    }

    private void setLyricsAlpha(float a, float ambient) {
        lyricsAlpha = a;
        if (lyricsView == null) return;
        lyricsView.setAlpha(a);
        lyricsView.setAmbient(ambient);
        int vis = a > 0.001f ? View.VISIBLE : View.INVISIBLE;
        if (lyricsView.getVisibility() != vis) lyricsView.setVisibility(vis);
    }

    /** El interruptor solo en el bloqueo (nunca en AOD), con letras en pantalla y si la app lo deja mostrar. */
    private void refreshToggle() {
        if (aodToggle == null) return;
        float a = toggleEligible ? lockAlpha : 0f;
        aodToggle.setAlpha(a);
        int vis = a > 0.001f ? View.VISIBLE : View.GONE;
        if (aodToggle.getVisibility() != vis) aodToggle.setVisibility(vis);
    }

    /** Toque en el interruptor: se aplica ya en memoria y se le pide a la app que lo guarde en sus ajustes. */
    private void onAodToggle() {
        cfg.aodLyrics = !cfg.aodLyrics;
        aodToggle.setOn(cfg.aodLyrics);
        // Corre en SystemUI, sin los recursos de la app: el texto sigue el idioma del sistema (español o inglés).
        boolean es = "es".equals(java.util.Locale.getDefault().getLanguage());
        aodToggle.showLabel(cfg.aodLyrics
                ? (es ? "Letras en pantalla ambiente" : "Lyrics on ambient display")
                : (es ? "Sin letras en pantalla ambiente" : "No lyrics on ambient display"));
        updateLyrics();
        try {
            ctx.sendBroadcast(new Intent(Config.ACTION_SET_AOD_LYRICS).setPackage(Config.PKG)
                    .putExtra(Config.K_AOD_LYRICS, cfg.aodLyrics));
        } catch (Throwable t) {
            log("no se pudo guardar el cambio en la app: " + t);
        }
    }

    private void setLockAlpha(float a) {
        lockAlpha = a;
        refreshToggle();
        if (container == null) return;
        container.setAlpha(a);
        int vis = a > 0.001f ? View.VISIBLE : View.INVISIBLE;
        if (container.getVisibility() != vis) container.setVisibility(vis);
    }

    // ---- Arranque en SystemUI ----

    /** Lo llama el módulo al cargar (o recargar) con las preferencias remotas que escribe la app. */
    void setPrefs(SharedPreferences p) {
        prefs = p;
        try {
            cfg.load(p);
            p.registerOnSharedPreferenceChangeListener(prefsListener);
        } catch (Throwable t) {
            log("sin preferencias remotas, se usan los ajustes de serie: " + t);
        }
    }

    private void init(Context c) {
        Context app = c.getApplicationContext();
        ctx = app != null ? app : c;
        workerThread = new HandlerThread("LockArt", Process.THREAD_PRIORITY_BACKGROUND);
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        registerReceiver();
        registerLyricsReceiver();
        aodGuard.init(ctx);
        watchWallpaper();
        watchMedia();
    }

    /** La app manda un ping (con permiso de firma) para saber si el gancho está; se responde con ACTION_STATUS. */
    private void registerReceiver() {
        pingReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                statusUntil = SystemClock.elapsedRealtime() + STATUS_WINDOW_MS;
                pushStatus();
            }
        };
        try {
            ctx.registerReceiver(pingReceiver, new IntentFilter(Config.ACTION_PING), Config.PERMISSION, main,
                    Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            pingReceiver = null;
            log("no se pudo registrar el receptor de la app: " + t);
        }
    }

    private void onPrefsChanged() {
        boolean wasPaused = cfg.keepPaused;
        boolean wasAuto = cfg.autoVeil;
        int wasBlur = cfg.blur;
        cfg.load(prefs);
        if (wasAuto != cfg.autoVeil && wallpaper != null) updateVeilColor();
        else applyVeil();
        if (wasBlur != cfg.blur) reprocess();
        if (wasPaused != cfg.keepPaused) scheduleRefresh();
        updateStage(true);
        updateLyrics();
        updateMusicMode(active == null ? null : active.getMetadata());
        pushStatus();
    }

    // ---- Recarga en caliente ----

    /**
     * Desmonta esta generación del módulo: quita la vista y suelta todo lo registrado en SystemUI, para que nada
     * retenga el classloader viejo. Devuelve el estado para la generación nueva, solo con objetos de SystemUI o del
     * sistema: {raíz, alfa del bloqueo, estado del bloqueo}.
     */
    Object[] teardown() {
        if (ctx != null) musicMode.update(ctx, false, null, null, null);
        musicMode.setForceSmallClock(false);
        Object[] mm = musicMode.captured();
        Object[] state = {root, lockAlpha, lockState, mm[0], mm[1], mm[2], mm[3], mm[4]};
        if (container != null && container.getParent() instanceof ViewGroup) {
            ((ViewGroup) container.getParent()).removeView(container);
        }
        if (aodToggle != null && aodToggle.getParent() instanceof ViewGroup) {
            ((ViewGroup) aodToggle.getParent()).removeView(aodToggle);
        }
        if (lyricsView != null && lyricsView.getParent() instanceof ViewGroup) {
            ((ViewGroup) lyricsView.getParent()).removeView(lyricsView);
        }
        aodGuard.stop();
        musicMode.setForceAod(false);
        main.removeCallbacksAndMessages(null);
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        for (MediaController c : controllers) c.unregisterCallback(mediaCallback);
        controllers.clear();
        try {
            if (sessions != null) sessions.removeOnActiveSessionsChangedListener(sessionsListener);
            if (wallpaper != null) wallpaper.removeOnColorsChangedListener(colorsListener);
            if (pingReceiver != null) ctx.unregisterReceiver(pingReceiver);
            if (lyricsReceiver != null) ctx.unregisterReceiver(lyricsReceiver);
        } catch (Throwable t) {
            log("desmontaje incompleto: " + t);
        }
        if (workerThread != null) workerThread.quitSafely();
        return state;
    }

    /** La generación nueva retoma la raíz y el estado del bloqueo sin esperar a otro onAttachedToWindow. */
    void resume(View rootView, float alpha, String state, Object[] captured) {
        musicMode.adopt(captured);
        lockAlpha = alpha;
        lockState = state;
        if (rootView != null) attach(rootView);
    }

    // ---- Velo ----

    private void watchWallpaper() {
        try {
            wallpaper = WallpaperManager.getInstance(ctx);
            updateVeilColor();
            wallpaper.addOnColorsChangedListener(colorsListener, main);
        } catch (Throwable t) {
            log("sin colores del fondo, velo negro: " + t);
        }
    }

    /**
     * El reloj y los iconos del bloqueo toman su color del fondo de pantalla, no de la carátula: si el fondo pide
     * texto oscuro, un velo negro lo dejaría ilegible, así que en ese caso el velo es blanco.
     */
    private void updateVeilColor() {
        boolean light = false;
        if (cfg.autoVeil && wallpaper != null) {
            try {
                WallpaperColors c = wallpaper.getWallpaperColors(WallpaperManager.FLAG_LOCK);
                if (c == null) c = wallpaper.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                light = c != null && (c.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0;
            } catch (Throwable t) {
                log("no se pudieron leer los colores del fondo: " + t);
            }
        }
        veilLight = light;
        applyVeil();
    }

    private void applyVeil() {
        if (lyricsView != null) lyricsView.setDark(veilLight);
        if (aodToggle != null) aodToggle.setDark(veilLight);
        if (veil == null) return;
        veil.setBackgroundColor(veilLight ? Color.WHITE : Color.BLACK);
        veil.setAlpha(cfg.dim / 100f);
    }

    // ---- Música ----

    private void watchMedia() {
        try {
            sessions = ctx.getSystemService(MediaSessionManager.class);
            sessions.addOnActiveSessionsChangedListener(sessionsListener, null, main);
            onSessionsChanged(sessions.getActiveSessions(null));
        } catch (Throwable t) {
            log("no se pudieron leer las sesiones multimedia: " + t);
        }
    }

    private void onSessionsChanged(List<MediaController> list) {
        for (MediaController c : controllers) c.unregisterCallback(mediaCallback);
        controllers.clear();
        if (list != null) {
            for (MediaController c : list) {
                c.registerCallback(mediaCallback, main);
                controllers.add(c);
            }
        }
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        main.removeCallbacks(refreshRunnable);
        main.post(refreshRunnable);
    }

    /** La primera sesión que suena (están ordenadas por prioridad); en pausa solo si el ajuste lo pide. */
    private MediaController choose() {
        MediaController paused = null;
        for (MediaController c : controllers) {
            PlaybackState ps = c.getPlaybackState();
            int s = ps == null ? PlaybackState.STATE_NONE : ps.getState();
            if (isPlaying(s)) return c;
            if (paused == null && s == PlaybackState.STATE_PAUSED) paused = c;
        }
        return cfg.keepPaused ? paused : null;
    }

    private static boolean isPlaying(int s) {
        switch (s) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return true;
            default:
                return false;
        }
    }

    private void refresh() {
        MediaController c = choose();
        final MediaMetadata md = c == null ? null : c.getMetadata();
        trackLyrics(c, md);
        String key = md == null ? null : key(c.getPackageName(), md);
        if (key == null) {
            scheduleClear();
            return;
        }
        cancelClear();
        if (key.equals(requestedKey)) return;
        requestedKey = key;
        final int gen = ++generation;
        final String p = c.getPackageName();
        final String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null || a.isEmpty()) a = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        final String ar = a;
        final int blur = cfg.blur;
        final String track = p + "|" + md.getString(MediaMetadata.METADATA_KEY_ALBUM) + "|" + t;
        worker.post(() -> {
            Bitmap loaded = load(p, md);
            // Spotify publica la carátula a 640 px y segundos después la reemplaza por una de 300 px: de la misma
            // canción se queda la más grande.
            if (loaded != null && track.equals(bestTrack) && bestArt != null && area(bestArt) > area(loaded)) {
                loaded = bestArt;
            } else if (loaded != null) {
                bestTrack = track;
                bestArt = loaded;
            }
            final Bitmap src = loaded;
            Bitmap disp = Blur.apply(src, blur);
            main.post(() -> {
                if (gen != generation) return;
                if (src != null && src == source) return;
                setArt(src, disp, blur, p, t, ar);
            });
        });
    }

    private static int area(Bitmap b) {
        return b.getWidth() * b.getHeight();
    }

    /** Identifica la carátula sin decodificarla; null si los metadatos no traen ninguna. */
    private static String key(String pkg, MediaMetadata md) {
        String art = null;
        for (String k : BITMAP_KEYS) {
            Bitmap b = md.getBitmap(k);
            if (b != null && !b.isRecycled()) {
                art = b.getWidth() + "x" + b.getHeight() + ":" + sample(b);
                break;
            }
        }
        if (art == null) {
            for (String k : URI_KEYS) {
                String u = md.getString(k);
                if (u != null && !u.isEmpty()) {
                    art = u;
                    break;
                }
            }
        }
        if (art == null) return null;
        return pkg + "|" + md.getString(MediaMetadata.METADATA_KEY_ALBUM) + "|"
                + md.getString(MediaMetadata.METADATA_KEY_TITLE) + "|" + art;
    }

    /** Huella de 36 píxeles: distingue dos carátulas del mismo tamaño sin recorrer todo el bitmap. */
    private static int sample(Bitmap b) {
        if (b.getConfig() == Bitmap.Config.HARDWARE) return b.getGenerationId();
        int h = 17;
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                h = h * 31 + b.getPixel((2 * x + 1) * b.getWidth() / 12, (2 * y + 1) * b.getHeight() / 12);
            }
        }
        return h;
    }

    private void scheduleClear() {
        if (clearPending || (shown == null && requestedKey == null)) return;
        clearPending = true;
        main.postDelayed(clearRunnable, CLEAR_DELAY_MS);
    }

    private void cancelClear() {
        if (!clearPending) return;
        clearPending = false;
        main.removeCallbacks(clearRunnable);
    }

    // ---- Carga (hilo de trabajo) ----

    private Bitmap load(String p, MediaMetadata md) {
        for (String k : BITMAP_KEYS) {
            Bitmap b = md.getBitmap(k);
            if (b != null && !b.isRecycled() && b.getWidth() > 1 && b.getHeight() > 1) {
                return b.getConfig() == Bitmap.Config.HARDWARE ? b.copy(Bitmap.Config.ARGB_8888, false) : b;
            }
        }
        for (String k : URI_KEYS) {
            String u = md.getString(k);
            if (u == null || u.isEmpty()) continue;
            Bitmap b = decodeUri(p, Uri.parse(u));
            if (b != null) return b;
        }
        return null;
    }

    private Bitmap decodeUri(String p, Uri uri) {
        if (!allowedUri(p, uri)) {
            log("carátula por URI no permitida para " + p + ": " + uri);
            return null;
        }
        try {
            ImageDecoder.Source s = ImageDecoder.createSource(ctx.getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(s, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int w = info.getSize().getWidth();
                int h = info.getSize().getHeight();
                int max = Math.max(w, h);
                if (max > MAX_ART_PX) {
                    float f = MAX_ART_PX / (float) max;
                    decoder.setTargetSize(Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f)));
                }
            });
        } catch (Throwable t) {
            log("no se pudo cargar la carátula " + uri + ": " + t);
            return null;
        }
    }

    /**
     * SystemUI puede leer casi cualquier proveedor de contenido; solo se aceptan URIs de la propia app que
     * reproduce o de MediaStore, para que una app no pueda poner en el bloqueo contenido de otra.
     */
    private boolean allowedUri(String p, Uri uri) {
        String scheme = uri.getScheme();
        String auth = uri.getAuthority();
        if (p == null || auth == null) return false;
        if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)) return p.equals(auth);
        if (!ContentResolver.SCHEME_CONTENT.equals(scheme)) return false;
        if (MediaStore.AUTHORITY.equals(auth)) return true;
        ProviderInfo pi = ctx.getPackageManager().resolveContentProvider(auth, 0);
        return pi != null && p.equals(pi.packageName);
    }

    // ---- Pintar ----

    private void setArt(Bitmap src, Bitmap disp, int blur, String p, String t, String a) {
        Bitmap previous = shown;
        source = src;
        shown = disp;
        pkg = p;
        title = t;
        artist = a;
        if (src != null && blur != cfg.blur) reprocess();
        if (stage != null) {
            if (disp != null) {
                if (previous != null && stageShown && lockAlpha > 0f) crossfade(previous, disp);
                else back.setImageBitmap(disp);
            }
            updateStage(true);
        }
        Log.d(TAG, src == null ? "sin carátula" : "carátula " + src.getWidth() + "x" + src.getHeight() + " de " + p + ": " + t);
        pushStatus();
    }

    private void crossfade(Bitmap previous, Bitmap next) {
        front.animate().cancel();
        front.setImageBitmap(previous);
        front.setAlpha(1f);
        front.setVisibility(View.VISIBLE);
        back.setImageBitmap(next);
        front.animate().alpha(0f).setDuration(CROSSFADE_MS).withEndAction(() -> {
            front.setVisibility(View.INVISIBLE);
            front.setImageDrawable(null);
        }).start();
    }

    /** Vuelve a desenfocar la carátula actual con el ajuste nuevo. */
    private void reprocess() {
        final Bitmap src = source;
        final int blur = cfg.blur;
        if (src == null || worker == null) return;
        worker.post(() -> {
            Bitmap disp = Blur.apply(src, blur);
            main.post(() -> {
                if (source != src) return;
                shown = disp;
                if (back != null) back.setImageBitmap(disp);
            });
        });
    }

    private void updateStage(boolean animate) {
        boolean show = cfg.enabled && shown != null;
        if (stage == null || show == stageShown) return;
        stageShown = show;
        stage.animate().cancel();
        boolean visible = animate && lockAlpha > 0f;
        if (show) {
            if (back.getDrawable() == null) back.setImageBitmap(shown);
            stage.setVisibility(View.VISIBLE);
            if (visible) stage.animate().alpha(1f).setDuration(FADE_MS).start();
            else stage.setAlpha(1f);
        } else if (visible) {
            stage.animate().alpha(0f).setDuration(FADE_MS).withEndAction(this::afterStageHidden).start();
        } else {
            stage.setAlpha(0f);
            afterStageHidden();
        }
    }

    private void afterStageHidden() {
        stage.setVisibility(View.INVISIBLE);
        if (shown == null) {
            back.setImageDrawable(null);
            front.setImageDrawable(null);
        }
    }

    // ---- Letras ----

    /**
     * Solo se aceptan letras mandadas por el proceso de Spotify: el remitente comparte su identidad
     * (BroadcastOptions.setShareIdentityEnabled) y aquí se comprueba con getSentFromPackage.
     */
    private void registerLyricsReceiver() {
        lyricsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!SpotifyLyrics.PACKAGE.equals(getSentFromPackage())) {
                    log("letra rechazada de " + getSentFromPackage());
                    return;
                }
                String track = intent.getStringExtra(Lyrics.EXTRA_TRACK);
                if (track == null) return;
                if (intent.getBooleanExtra("none", false)) {
                    lyricsByTrack.remove(track);
                    noLyrics.add(track);
                } else {
                    Lyrics l = Lyrics.fromBundle(intent.getExtras());
                    if (l == null) return;
                    lyricsByTrack.put(track, l);
                    noLyrics.remove(track);
                }
                updateLyrics();
                pushStatus();
            }
        };
        try {
            ctx.registerReceiver(lyricsReceiver, new IntentFilter(Config.ACTION_LYRICS), null, main,
                    Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            lyricsReceiver = null;
            log("no se pudo registrar el receptor de letras: " + t);
        }
    }

    /** Sigue la sesión elegida; si es Spotify y aún no hay letra de esa canción, se la pide a su proceso. */
    private void trackLyrics(MediaController c, MediaMetadata md) {
        active = c;
        activeState = c == null ? null : c.getPlaybackState();
        String id = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        activeTrack = c != null && SpotifyLyrics.PACKAGE.equals(c.getPackageName()) && id != null
                && id.startsWith("spotify:track:") ? id.substring("spotify:track:".length()) : null;
        if (activeTrack != null && !lyricsByTrack.containsKey(activeTrack) && !noLyrics.contains(activeTrack)
                && !activeTrack.equals(requestedLyrics) && ctx != null) {
            requestedLyrics = activeTrack;
            try {
                ctx.sendBroadcast(new Intent(Config.ACTION_LYRICS_REQUEST).setPackage(SpotifyLyrics.PACKAGE));
            } catch (Throwable t) {
                log("no se pudo pedir la letra a Spotify: " + t);
            }
        }
        updateLyrics();
        updateMusicMode(md);
    }

    /** Modo música activo solo mientras la sesión elegida está sonando de verdad (no en pausa). */
    private void updateMusicMode(MediaMetadata md) {
        if (ctx == null) return;
        boolean playing = activeState != null && isPlaying(activeState.getState());
        boolean on = cfg.musicMode && playing && md != null;
        musicMode.update(ctx, on, active == null ? null : active.getPackageName(),
                md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE),
                md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST));
    }

    private void updateLyrics() {
        if (lyricsView == null) return;
        lyricsView.setAnimation(cfg.lyricsAnim);
        Lyrics l = cfg.lyrics && activeTrack != null ? lyricsByTrack.get(activeTrack) : null;
        lyricsView.setLyrics(l);
        lyricsView.invalidate();
        // Con letras en pantalla se mantiene el reloj pequeño: el grande ocupa la franja de las letras.
        musicMode.setForceSmallClock(l != null || musicMode.isActive());
        // AOD solo mientras suena algo con letra y la opción está activa; si no, tus ajustes de pantalla ambiente.
        boolean playing = activeState != null && isPlaying(activeState.getState());
        aodGuard.setWanted(cfg.aodLyrics && l != null && playing);
        toggleEligible = cfg.aodToggle && l != null;
        if (aodToggle != null) aodToggle.setOn(cfg.aodLyrics);
        refreshToggle();
    }

    /** Posición estimada desde el último PlaybackState: posición + tiempo transcurrido x velocidad. */
    private long positionNow() {
        PlaybackState ps = activeState;
        if (ps == null) return 0L;
        long p = ps.getPosition();
        if (ps.getState() == PlaybackState.STATE_PLAYING && ps.getLastPositionUpdateTime() > 0) {
            p += (long) ((SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime()) * ps.getPlaybackSpeed());
        }
        return p;
    }

    // ---- Estado para la app ----

    private void pushStatus() {
        if (ctx == null || worker == null || SystemClock.elapsedRealtime() > statusUntil) return;
        final Intent i = new Intent(Config.ACTION_STATUS).setPackage(Config.PKG);
        i.putExtra("hooks", hookInfo);
        i.putExtra("attached", container != null && container.getParent() != null);
        i.putExtra("root", root == null ? null : root.getClass().getSimpleName());
        i.putExtra("lock", lockState);
        i.putExtra("sessions", controllers.size());
        i.putExtra("pkg", pkg);
        i.putExtra("title", title);
        i.putExtra("artist", artist);
        i.putExtra("veil_light", veilLight);
        Lyrics l = activeTrack == null ? null : lyricsByTrack.get(activeTrack);
        i.putExtra("lyrics_lines", l == null ? 0 : l.size());
        i.putExtra("music_mode", musicMode.isActive());
        final Bitmap src = source;
        worker.post(() -> {
            if (src != null) i.putExtra("thumb", jpeg(src));
            try {
                ctx.sendBroadcast(i);
            } catch (Throwable t) {
                log("no se pudo avisar a la app: " + t);
            }
        });
    }

    private static byte[] jpeg(Bitmap src) {
        float f = Math.min(1f, THUMB_PX / (float) Math.max(src.getWidth(), src.getHeight()));
        Bitmap b = f < 1f ? Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * f)),
                Math.max(1, Math.round(src.getHeight() * f)), true) : src;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 88, out);
        return out.toByteArray();
    }
}
