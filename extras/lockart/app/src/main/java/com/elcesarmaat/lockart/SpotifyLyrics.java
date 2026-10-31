package com.elcesarmaat.lockart;

import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;

/**
 * Proveedor de letras dentro del proceso de Spotify, adaptado del proveedor spotify-music de LyricProvider
 * (tomakino, Apache-2.0):
 *
 * - MediaSession.setMetadata (clase del sistema, estable) da el id de la canción ("spotify:track:...").
 * - El constructor de okhttp3.Headers deja copiar las cabeceras de sesión que Spotify ya manda. Spotify ofusca el
 *   nombre de la clase (en 9.1.90.2270 es p.ad40), así que se busca con DexKit: es lo que devuelve el método que usa
 *   "Expected alternating header names and values" (Headers.of). El resultado se guarda por versión de Spotify.
 * - Con esas cabeceras se pide la letra a color-lyrics/v2, el mismo servidor de la pestaña de letras de Spotify.
 *
 * La letra se manda a SystemUI con la identidad del remitente, así SystemUI solo acepta letras de Spotify.
 * Nunca se registra el texto de las letras en el log.
 */
final class SpotifyLyrics {

    static final String PACKAGE = "com.spotify.music";
    private static final String TAG = "LockArt";
    private static final String HEADERS_OF = "Expected alternating header names and values";
    /** OkHttp oculta los valores de cabeceras sensibles con esto en Headers.toString(). */
    private static final String HEADERS_REDACTED = "\u2588\u2588";
    private static final String[] KEYS = {"authorization", "client-token", "user-agent", "x-client-id"};
    private static final String[] HOSTS = {"https://guc3-spclient.spotify.com", "https://spclient.wg.spotify.com"};
    private static final int MEMORY_CACHE = 30;
    private static final int DISK_CACHE = 300;

    private static SpotifyLyrics instance;

    static synchronized SpotifyLyrics get() {
        if (instance == null) instance = new SpotifyLyrics();
        return instance;
    }

    private XposedInterface x;
    private ApplicationInfo app;
    private HandlerThread thread;
    private Handler worker;
    private BroadcastReceiver requestReceiver;
    private String headersClass = "?";
    /** Qué huella encontró la clase de cabeceras, para el log y la app. */
    private String headersVia = "ninguna";

    private final Map<String, String> headers = new ConcurrentHashMap<>();
    /** Solo en el hilo de trabajo. */
    private final Map<String, Lyrics> memory = new LinkedHashMap<String, Lyrics>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Lyrics> e) {
            return size() > MEMORY_CACHE;
        }
    };
    private final Map<String, Boolean> missing = new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) {
            return size() > MEMORY_CACHE;
        }
    };
    private volatile String current;
    /** Canción que espera cabeceras válidas para pedirse. */
    private volatile String pending;

    private SpotifyLyrics() {
    }

    void install(XposedInterface xposed, ClassLoader cl, ApplicationInfo info) {
        x = xposed;
        app = info;
        thread = new HandlerThread("LockArtLyrics", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        worker = new Handler(thread.getLooper());

        try {
            x.hook(MediaSession.class.getDeclaredMethod("setMetadata", MediaMetadata.class)).intercept(chain -> {
                Object r = chain.proceed();
                Object md = chain.getArg(0);
                if (md instanceof MediaMetadata) onMetadata((MediaMetadata) md);
                return r;
            });
        } catch (Throwable t) {
            log(Log.WARN, "sin enganche de MediaSession.setMetadata: " + t);
        }
        // Buscar la clase con DexKit tarda un poco: se hace en el hilo de trabajo para no frenar el arranque.
        worker.post(() -> hookHeaders(cl));
    }

    /** Para la recarga en caliente: suelta el receptor y el hilo (los enganches los quita el framework). */
    void teardown() {
        try {
            Context c = context();
            if (requestReceiver != null && c != null) c.unregisterReceiver(requestReceiver);
        } catch (Throwable ignored) {
        }
        requestReceiver = null;
        if (thread != null) thread.quitSafely();
    }

    String status() {
        return "Headers=" + headersClass + " (" + headersVia + ")"
                + (headers.containsKey("authorization") ? " con sesión" : " sin sesión");
    }

    // ---- Cabeceras de sesión ----

    private void hookHeaders(ClassLoader cl) {
        Class<?> cls = findHeaders(cl);
        if (cls == null) {
            log(Log.WARN, "no se encontró la clase Headers de OkHttp; sin letras");
            return;
        }
        try {
            Constructor<?> ctor = cls.getDeclaredConstructor(String[].class);
            x.hook(ctor).intercept(chain -> {
                Object r = chain.proceed();
                Object a = chain.getArg(0);
                if (a instanceof String[]) capture((String[]) a);
                return r;
            });
            headersClass = cls.getName();
            log(Log.INFO, "cabeceras de Spotify vía " + headersClass + " (huella: " + headersVia + ")");
        } catch (Throwable t) {
            log(Log.WARN, "no se pudo enganchar " + cls.getName() + ": " + t);
        }
    }

    /**
     * Busca okhttp3.Headers con varias huellas, de la más rápida a la más tolerante, como hacen los módulos que
     * sobreviven a las actualizaciones: ninguna depende del nombre ofuscado. Cada candidato se valida por estructura
     * (isHeaders) antes de engancharlo, y el resultado se guarda por versión instalada de Spotify.
     *
     * 1. Caché de esta versión.
     * 2. Nombre real (por si Spotify dejara de ofuscar OkHttp).
     * 3. Texto: lo que devuelve Headers.of(), el método con "Expected alternating header names and values".
     * 4. Texto: la clase cuyo toString() oculta valores sensibles con "██".
     * 5. Estructura: Iterable con un campo String[], constructor (String[]) y un método que devuelve TreeMap.
     */
    private Class<?> findHeaders(ClassLoader cl) {
        File cache = new File(app.dataDir, "cache/lockart_headers");
        File apk = new File(app.sourceDir);
        String version = apk.length() + ":" + apk.lastModified();
        String cached = read(cache);
        if (cached != null && cached.startsWith(version + "=")) {
            Class<?> c = headersCandidate(cl, cached.substring(version.length() + 1));
            if (c != null) {
                headersVia = "caché";
                return c;
            }
        }
        Class<?> c = headersCandidate(cl, "okhttp3.Headers");
        if (c != null) headersVia = "nombre real";
        if (c == null) {
            try {
                System.loadLibrary("dexkit");
                try (DexKitBridge bridge = DexKitBridge.create(app.sourceDir)) {
                    // Con cada versión nueva de Spotify se prueban las tres huellas y se registra cuáles siguen
                    // funcionando: así se sabe que una se rompió antes de que fallen todas.
                    Class<?> of = byReturnType(bridge, cl, HEADERS_OF);
                    Class<?> str = byDeclaringClass(bridge, cl, HEADERS_REDACTED);
                    Class<?> shape = byStructure(bridge, cl);
                    log(Log.INFO, "huellas de Headers: Headers.of=" + name(of) + ", toString=" + name(str)
                            + ", estructura=" + name(shape));
                    c = of != null ? of : (str != null ? str : shape);
                    headersVia = of != null ? "texto de Headers.of" : str != null ? "texto de toString"
                            : shape != null ? "estructura" : "ninguna";
                }
            } catch (Throwable t) {
                log(Log.WARN, "DexKit falló: " + t);
            }
        }
        if (c != null) write(cache, version + "=" + c.getName());
        return c;
    }

    private Class<?> byReturnType(DexKitBridge bridge, ClassLoader cl, String text) {
        for (MethodData m : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().usingStrings(text)))) {
            Class<?> c = headersCandidate(cl, m.getReturnTypeName());
            if (c != null) return c;
        }
        log(Log.INFO, "huella sin resultado: Headers.of");
        return null;
    }

    private Class<?> byDeclaringClass(DexKitBridge bridge, ClassLoader cl, String text) {
        for (MethodData m : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().usingStrings(text)))) {
            Class<?> c = headersCandidate(cl, m.getClassName());
            if (c != null) return c;
        }
        log(Log.INFO, "huella sin resultado: toString");
        return null;
    }

    private Class<?> byStructure(DexKitBridge bridge, ClassLoader cl) {
        ClassMatcher m = ClassMatcher.create()
                .addField(FieldMatcher.create().type("java.lang.String[]"))
                .addMethod(MethodMatcher.create().name("<init>").paramTypes("java.lang.String[]"))
                .addMethod(MethodMatcher.create().returnType("java.util.TreeMap"));
        for (ClassData d : bridge.findClass(FindClass.create().matcher(m))) {
            Class<?> c = headersCandidate(cl, d.getName());
            if (c != null) return c;
        }
        log(Log.INFO, "huella sin resultado: estructura");
        return null;
    }

    private static String name(Class<?> c) {
        return c == null ? "no" : c.getName();
    }

    private static Class<?> headersCandidate(ClassLoader cl, String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            Class<?> c = Class.forName(name, false, cl);
            return isHeaders(c) ? c : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Lo que define a okhttp3.Headers sin mirar nombres: Iterable, un campo String[] y constructor (String[]). */
    private static boolean isHeaders(Class<?> c) {
        try {
            c.getDeclaredConstructor(String[].class);
        } catch (NoSuchMethodException e) {
            return false;
        }
        if (!Iterable.class.isAssignableFrom(c)) return false;
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (f.getType() == String[].class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) return true;
        }
        return false;
    }

    /** OkHttp guarda nombres y valores intercalados: [nombre0, valor0, nombre1, valor1, ...]. */
    private void capture(String[] nv) {
        boolean newAuth = false;
        for (int i = 0; i + 1 < nv.length; i += 2) {
            if (nv[i] == null || nv[i + 1] == null) continue;
            String key = nv[i].toLowerCase(Locale.ROOT);
            for (String k : KEYS) {
                if (k.equals(key)) {
                    String old = headers.put(k, nv[i + 1]);
                    if (k.equals("authorization") && !nv[i + 1].equals(old)) newAuth = true;
                }
            }
        }
        String p = pending;
        if (newAuth && p != null && worker != null) {
            pending = null;
            worker.post(() -> load(p));
        }
    }

    // ---- Canción actual ----

    private void onMetadata(MediaMetadata md) {
        String id = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if (id == null || !id.startsWith("spotify:track:")) return;
        id = id.substring("spotify:track:".length());
        if (id.isEmpty() || id.equals(current)) return;
        current = id;
        final String track = id;
        if (worker != null) worker.post(() -> load(track));
    }

    /** Hilo de trabajo: memoria → disco → red. */
    private void load(String track) {
        ensureReceiver();
        Lyrics l = memory.get(track);
        if (l == null && missing.containsKey(track)) {
            send(track, null);
            return;
        }
        if (l == null) {
            l = SpotifyProto.parse(track, readBytes(diskFile(track)));
            if (l != null) memory.put(track, l);
        }
        if (l == null) {
            if (!headers.containsKey("authorization")) {
                pending = track;
                return;
            }
            l = fetch(track);
        }
        if (track.equals(current)) send(track, l);
    }

    private Lyrics fetch(String track) {
        for (String host : HOSTS) {
            HttpURLConnection c = null;
            try {
                URL url = new URL(host + "/color-lyrics/v2/track/" + track + "?vocalRemoval=true&clientLanguage="
                        + Locale.getDefault().toLanguageTag() + "&preview=false");
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("accept", "application/protobuf");
                c.setRequestProperty("app-platform", "Android");
                for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
                int code = c.getResponseCode();
                if (code == 404) {
                    missing.put(track, true);
                    log(Log.DEBUG, "sin letra en Spotify para " + track);
                    return null;
                }
                if (code == 401 || code == 403) {
                    // Token caducado: se reintenta cuando Spotify mande uno nuevo.
                    headers.remove("authorization");
                    pending = track;
                    log(Log.DEBUG, "sesión caducada (" + code + "), esperando cabeceras nuevas");
                    return null;
                }
                if (code != 200) {
                    log(Log.DEBUG, "HTTP " + code + " en " + host);
                    continue;
                }
                byte[] body = readAll(c.getInputStream());
                Lyrics l = SpotifyProto.parse(track, body);
                if (l == null) {
                    missing.put(track, true);
                    log(Log.DEBUG, "letra no sincronizada o ilegible para " + track);
                    return null;
                }
                memory.put(track, l);
                writeBytes(diskFile(track), body);
                log(Log.INFO, "letra de " + track + ": " + l.size() + " líneas"
                        + (l.wStart.length > 0 ? ", " + l.wStart.length + " palabras con tiempo" : ""));
                return l;
            } catch (Throwable t) {
                log(Log.DEBUG, "fallo al pedir la letra en " + host + ": " + t);
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    // ---- Envío a SystemUI ----

    /** l == null avisa de que esta canción no tiene letra, para que SystemUI no la siga pidiendo. */
    private void send(String track, Lyrics l) {
        Context c = context();
        if (c == null) return;
        Intent i = new Intent(Config.ACTION_LYRICS).setPackage("com.android.systemui");
        if (l != null) l.toIntent(i);
        else i.putExtra(Lyrics.EXTRA_TRACK, track).putExtra("none", true);
        BroadcastOptions o = BroadcastOptions.makeBasic();
        o.setShareIdentityEnabled(true);
        try {
            c.sendBroadcast(i, null, o.toBundle());
        } catch (Throwable t) {
            log(Log.WARN, "no se pudo mandar la letra a SystemUI: " + t);
        }
    }

    /** SystemUI pide la letra actual cuando arranca o la perdió; solo él tiene STATUS_BAR_SERVICE. */
    private void ensureReceiver() {
        if (requestReceiver != null) return;
        Context c = context();
        if (c == null) return;
        requestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String track = current;
                if (track != null) load(track);
            }
        };
        try {
            c.registerReceiver(requestReceiver, new IntentFilter(Config.ACTION_LYRICS_REQUEST),
                    "android.permission.STATUS_BAR_SERVICE", worker, Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            requestReceiver = null;
            log(Log.WARN, "no se pudo registrar el receptor de peticiones: " + t);
        }
    }

    private static Context context() {
        try {
            return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- Disco (dentro de los datos de Spotify) ----

    private File diskFile(String track) {
        File dir = new File(app.dataDir, "cache/lockart_lyrics");
        return new File(dir, track.replaceAll("[^A-Za-z0-9]", "") + ".pb");
    }

    private static byte[] readBytes(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeBytes(File f, byte[] b) {
        try {
            File dir = f.getParentFile();
            if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return;
            File[] old = dir.listFiles();
            if (old != null && old.length >= DISK_CACHE) {
                File oldest = old[0];
                for (File o : old) if (o.lastModified() < oldest.lastModified()) oldest = o;
                //noinspection ResultOfMethodCallIgnored
                oldest.delete();
            }
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(b);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String read(File f) {
        byte[] b = readBytes(f);
        return b == null ? null : new String(b, StandardCharsets.UTF_8).trim();
    }

    private static void write(File f, String s) {
        writeBytes(f, s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private void log(int priority, String msg) {
        if (x != null) x.log(priority, TAG, "[Spotify] " + msg, null);
        else Log.println(priority, TAG, "[Spotify] " + msg);
    }
}
