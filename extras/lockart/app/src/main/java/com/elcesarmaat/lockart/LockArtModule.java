package com.elcesarmaat.lockart;

import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Entrada del módulo con la API moderna de LSPosed (libxposed 102). Dos enganches en SystemUI, verificados en
 * SystemUIGoogle de Android 17 CP3A.261005.005 con Scene Container:
 *
 * - WindowRootView.onAttachedToWindow: la raíz de la ventana del panel/bloqueo. Con Scene Container es
 *   SceneWindowRootView y sus hijos son la escena Compose, las notificaciones, el bouncer y el auth ripple; la
 *   carátula se mete en el índice 0, debajo de todo eso y encima del fondo de pantalla (otra ventana).
 * - KeyguardTransitionRepositoryImpl.emitTransition(TransitionStep, boolean): por ahí pasan todos los pasos de
 *   transición del bloqueo (from, to, value), así que la carátula sigue fotograma a fotograma la animación real
 *   de encender, apagar a AOD o desbloquear.
 *
 * En el proceso principal de Spotify instala SpotifyLyrics, que manda a SystemUI la letra sincronizada.
 *
 * Con recarga en caliente (module.prop: autoHotReload): al instalar una versión nueva, la generación vieja quita su
 * vista y le pasa a la nueva el classloader de SystemUI, la raíz y el estado del bloqueo; la nueva reemplaza los
 * enganches sin reiniciar SystemUI.
 */
public class LockArtModule extends XposedModule {

    private static final String TAG = "LockArt";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String CLS_ROOT = "com.android.systemui.scene.ui.view.WindowRootView";
    /** Sin Scene Container (Android 14-16) la raíz es esta; también hereda de WindowRootView desde Android 15. */
    private static final String CLS_ROOT_LEGACY = "com.android.systemui.shade.NotificationShadeWindowView";
    private static final String CLS_TRANSITIONS = "com.android.systemui.keyguard.data.repository.KeyguardTransitionRepositoryImpl";
    private static final String CLS_STEP = "com.android.systemui.keyguard.shared.model.TransitionStep";

    private ClassLoader systemUi;
    /** En el proceso de Spotify: su classloader y ApplicationInfo, para el proveedor de letras. */
    private ClassLoader spotify;
    private android.content.pm.ApplicationInfo spotifyInfo;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        String process = param.getProcessName();
        if (!SYSTEMUI.equals(process) && !SpotifyLyrics.PACKAGE.equals(process)) {
            detach();
            return;
        }
        ArtController.setLogger(this);
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.isFirstPackage()) return;
        if (SpotifyLyrics.PACKAGE.equals(param.getPackageName())) {
            spotify = param.getClassLoader();
            spotifyInfo = param.getApplicationInfo();
            SpotifyLyrics.get().install(this, spotify, spotifyInfo);
            return;
        }
        if (!SYSTEMUI.equals(param.getPackageName())) return;
        systemUi = param.getClassLoader();
        ArtController.get().setPrefs(getRemotePreferences(Config.PREFS));
        install(systemUi);
    }

    @Override
    public boolean onHotReloading(@NonNull HotReloadingParam param) {
        if (spotify != null) {
            SpotifyLyrics.get().teardown();
            param.setSavedInstanceState(new Object[]{SpotifyLyrics.PACKAGE, spotify, spotifyInfo});
            return true;
        }
        if (systemUi == null) return true;
        // La vista vieja se quita en el hilo principal; aquí solo se pasan objetos de SystemUI o del sistema.
        final Object[] state = new Object[1];
        CountDownLatch done = new CountDownLatch(1);
        ArtController.runOnMain(() -> {
            state[0] = ArtController.get().teardown();
            done.countDown();
        });
        try {
            done.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        Object[] saved = state[0] instanceof Object[] ? (Object[]) state[0] : new Object[]{null, 0f, "OFF", null, null, null, null, null};
        param.setSavedInstanceState(new Object[]{SYSTEMUI, systemUi, saved[0], saved[1], saved[2],
                new Object[]{saved[3], saved[4], saved[5], saved[6], saved[7]}});
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull HotReloadedParam param) {
        for (XposedInterface.HookHandle h : param.getOldHookHandles()) h.unhook();
        ArtController.setLogger(this);
        Object s = param.getSavedInstanceState();
        Object[] saved = s instanceof Object[] ? (Object[]) s : null;
        if (saved == null || saved.length < 3 || !(saved[1] instanceof ClassLoader)) {
            ArtController.log("recarga sin estado en " + param.getProcessName() + "; hará falta reiniciarlo");
            return;
        }
        if (SpotifyLyrics.PACKAGE.equals(saved[0])) {
            spotify = (ClassLoader) saved[1];
            spotifyInfo = (android.content.pm.ApplicationInfo) saved[2];
            SpotifyLyrics.get().install(this, spotify, spotifyInfo);
            ArtController.log("recargado en caliente en Spotify");
            return;
        }
        systemUi = (ClassLoader) saved[1];
        ArtController art = ArtController.get();
        art.setPrefs(getRemotePreferences(Config.PREFS));
        install(systemUi);
        art.resume(saved[2] instanceof View ? (View) saved[2] : null,
                saved.length > 3 && saved[3] instanceof Float ? (Float) saved[3] : 0f,
                saved.length > 4 && saved[4] instanceof String ? (String) saved[4] : "OFF",
                saved.length > 5 && saved[5] instanceof Object[] ? (Object[]) saved[5] : null);
        ArtController.log("recargado en caliente");
    }

    private void install(ClassLoader cl) {
        final ArtController art = ArtController.get();

        String root = "ninguna";
        Class<?> rootCls = find(CLS_ROOT, cl);
        if (rootCls == null) rootCls = find(CLS_ROOT_LEGACY, cl);
        if (rootCls != null) {
            try {
                Method m = rootCls.getDeclaredMethod("onAttachedToWindow");
                hook(m).intercept(chain -> {
                    Object r = chain.proceed();
                    art.attach(((View) chain.getThisObject()).getRootView());
                    return r;
                });
                root = rootCls.getSimpleName();
            } catch (Throwable t) {
                log(Log.WARN, TAG, "sin enganche de la raíz: " + t, null);
            }
        }

        String steps = "no";
        Class<?> repo = find(CLS_TRANSITIONS, cl);
        Class<?> stepCls = find(CLS_STEP, cl);
        if (repo != null && stepCls != null) {
            try {
                final Field from = field(stepCls, "from");
                final Field to = field(stepCls, "to");
                final Field value = field(stepCls, "value");
                final Field state = field(stepCls, "transitionState");
                for (Method m : repo.getDeclaredMethods()) {
                    if (!m.getName().equals("emitTransition") || m.getParameterCount() == 0) continue;
                    hook(m).intercept(chain -> {
                        Object r = chain.proceed();
                        Object step = chain.getArg(0);
                        art.onKeyguardStep(name(from.get(step)), name(to.get(step)), value.getFloat(step),
                                name(state.get(step)));
                        return r;
                    });
                    steps = "sí";
                }
            } catch (Throwable t) {
                log(Log.WARN, TAG, "sin enganche de transiciones: " + t, null);
            }
        }

        art.musicMode().install(this, cl);

        art.setHookInfo("API " + getApiVersion() + " · " + getFrameworkName() + " " + getFrameworkVersion()
                + " · raíz=" + root + " transiciones=" + steps + " · modo música: " + art.musicMode().info());
        ArtController.log("cargado en SystemUI: " + art.hookInfo());
    }

    private static Class<?> find(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static String name(Object e) {
        return e instanceof Enum ? ((Enum<?>) e).name() : String.valueOf(e);
    }
}
