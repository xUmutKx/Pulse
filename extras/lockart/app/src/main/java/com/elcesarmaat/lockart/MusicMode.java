package com.elcesarmaat.lockart;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Process;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * «Modo música»: mientras suena algo, el bloqueo oculta la tarjeta del reproductor y las notificaciones, y el
 * smartspace bajo el reloj muestra la canción (título, icono y artista). Todo en memoria dentro de SystemUI: no se
 * tocan los ajustes del sistema, y al parar la música vuelve a mostrarse lo de siempre. Al bajar el panel el
 * reproductor sigue ahí, porque el panel usa otro modelo de medios.
 *
 * Enganches (SystemUIGoogle de Android 17 CP3A.261005.005):
 * - KeyguardMediaViewModel.isMediaVisible(): solo lo usa la escena Compose del bloqueo. Para que Compose se entere al
 *   cambiar de modo, el enganche lee un MutableState propio creado con el runtime de Compose de SystemUI.
 * - KeyguardNotificationVisibilityProviderImpl.shouldHideNotification(entry): oculta cada notificación mientras el
 *   teléfono está bloqueado; notifyStateChanged() hace que la lista se vuelva a filtrar.
 * - LockscreenSmartspaceController.updateMediaTarget(): el objetivo «deviceMedia» (tipo 41) que Google construye en
 *   KeyguardMediaViewController ya no llega en Android 17 (escucha a NotificationMediaManager, que no avisa), así que
 *   se construye aquí igual y se pone en storedMediaTarget.
 */
final class MusicMode {

    private static final String CLS_MEDIA_VM = "com.android.systemui.keyguard.ui.viewmodel.KeyguardMediaViewModel";
    private static final String CLS_NOTIF = "com.android.systemui.statusbar.notification.interruption.KeyguardNotificationVisibilityProviderImpl";
    private static final String CLS_SMARTSPACE = "com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController";
    private static final String CLS_SNAPSHOT = "androidx.compose.runtime.SnapshotStateKt";
    private static final String CLS_MUTABLE_STATE = "androidx.compose.runtime.MutableState";
    private static final ComponentName MEDIA_COMPONENT = new ComponentName("com.android.systemui",
            "com.google.android.systemui.smartspace.KeyguardMediaViewController");

    private boolean active;
    private Object notifProvider;
    private Method notifyStateChanged;
    private Object smartspace;
    private Field storedMediaTarget;
    private Method updateMediaTarget;
    /** MutableState<Boolean> de Compose: lo lee el enganche de isMediaVisible para que el cambio recomponga. */
    private Object composeState;
    private Method stateSet;
    private Method stateGet;
    private Object target;
    private String targetKey;
    private String info = "sin instalar";
    private static final String CLOCK_KEY = "lockscreen_use_double_line_clock";
    private static final Uri CLOCK_URI = Settings.Secure.getUriFor(CLOCK_KEY);
    private volatile boolean forceSmallClock;
    private ContentObserver clockObserver;
    private volatile boolean forceAod;
    private Object dozeParameters;
    private Method onTuningChanged;
    /**
     * DozeMachine: DozeSuppressor solo cambia entre AOD y pantalla apagada por ahorro de batería o supresión, no al
     * cambiar alwaysOn, así que se le pide el estado directamente (DOZE_AOD <-> DOZE).
     */
    private Object dozeMachine;
    private Method requestState;
    private Method getState;
    private Object stateDoze;
    private Object stateAod;

    boolean isActive() {
        return active;
    }

    /** Objetos de SystemUI capturados, para pasarlos a la siguiente generación en la recarga en caliente. */
    Object[] captured() {
        return new Object[]{smartspace, notifProvider, clockObserver, dozeParameters, dozeMachine};
    }

    void adopt(Object[] c) {
        if (c == null) return;
        if (c.length > 0 && c[0] != null) smartspace = c[0];
        if (c.length > 1 && c[1] != null) notifProvider = c[1];
        if (c.length > 2 && c[2] instanceof ContentObserver) clockObserver = (ContentObserver) c[2];
        if (c.length > 3 && c[3] != null) dozeParameters = c[3];
        if (c.length > 4 && c[4] != null) dozeMachine = c[4];
    }

    /**
     * Reloj pequeño forzado: KeyguardClockRepositoryImpl.getClockSize() lee el ajuste «reloj de dos líneas»
     * (lockscreen_use_double_line_clock) cada vez que su observador avisa; mientras se fuerza, devuelve SMALL en
     * memoria y se dispara ese observador para que el bloqueo lo aplique ya. No se escribe ningún ajuste.
     */
    boolean isAodForced() {
        return forceAod;
    }

    void setForceAod(boolean on) {
        if (on == forceAod) return;
        forceAod = on;
        try {
            if (dozeParameters != null) onTuningChanged.invoke(dozeParameters, "doze_always_on", null);
            if (dozeMachine != null) {
                Object cur = getState.invoke(dozeMachine);
                // Con la proximidad tapada el propio Pixel pasa por DOZE_AOD_PAUSING/PAUSED; cualquier estado de AOD
                // (salvo los pulsos de notificación) se manda a DOZE.
                String name = cur instanceof Enum ? ((Enum<?>) cur).name() : String.valueOf(cur);
                if (!on && name.startsWith("DOZE_AOD")) requestState.invoke(dozeMachine, stateDoze);
                else if (on && cur == stateDoze) requestState.invoke(dozeMachine, stateAod);
                ArtController.log("pantalla ambiente: estado " + name + " -> " + (on ? "AOD" : "apagada"));
            } else {
                ArtController.log("pantalla ambiente: DozeMachine sin capturar");
            }
            ArtController.log("pantalla ambiente con letras " + (on ? "activada" : "desactivada")
                    + (dozeParameters == null ? " (DozeParameters sin capturar)" : ""));
        } catch (Throwable t) {
            ArtController.log("no se pudo avisar del cambio de pantalla ambiente: " + t);
        }
    }

    void setForceSmallClock(boolean on) {
        if (on == forceSmallClock) return;
        forceSmallClock = on;
        ContentObserver o = clockObserver;
        if (o != null) o.dispatchChange(false, CLOCK_URI);
    }

    String info() {
        return info;
    }

    // Corre dentro de SystemUI (app de plataforma), donde las APIs ocultas no están restringidas.
    @SuppressLint({"BlockedPrivateApi", "PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi"})
    void install(XposedInterface x, ClassLoader cl) {
        StringBuilder ok = new StringBuilder();
        try {
            // Se pide el método exacto: recorrer todos los de SnapshotStateKt falla porque R8 quitó clases de firmas
            // que SystemUI no usa (ClassNotFoundException).
            Class<?> snapshot = Class.forName(CLS_SNAPSHOT, false, cl);
            Class<?> policy = Class.forName("androidx.compose.runtime.SnapshotMutationPolicy", false, cl);
            Method of = snapshot.getDeclaredMethod("mutableStateOf$default", Object.class, policy, int.class, Object.class);
            Class<?> ms = Class.forName(CLS_MUTABLE_STATE, false, cl);
            composeState = of.invoke(null, Boolean.FALSE, null, 2, null);
            stateSet = ms.getMethod("setValue", Object.class);
            stateGet = Class.forName("androidx.compose.runtime.State", false, cl).getMethod("getValue");
        } catch (Throwable t) {
            composeState = null;
            ArtController.log("modo música: sin estado de Compose, el reproductor cambia al recomponer: " + t);
        }
        try {
            Method visible = Class.forName(CLS_MEDIA_VM, false, cl).getDeclaredMethod("isMediaVisible");
            x.hook(visible).intercept(chain -> {
                Object r = chain.proceed();
                boolean hide = readState();
                return hide ? Boolean.FALSE : r;
            });
            ok.append("reproductor ");
        } catch (Throwable t) {
            ArtController.log("modo música: sin enganche del reproductor: " + t);
        }

        try {
            Class<?> c = Class.forName(CLS_NOTIF, false, cl);
            notifyStateChanged = c.getDeclaredMethod("notifyStateChanged", String.class);
            final Method locked = c.getDeclaredMethod("isLockedOrLocking");
            Method shouldHide = null;
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("shouldHideNotification") && m.getParameterCount() == 1) shouldHide = m;
            }
            if (shouldHide != null) {
                x.hook(shouldHide).intercept(chain -> {
                    Object provider = chain.getThisObject();
                    notifProvider = provider;
                    Object r = chain.proceed();
                    if (active && !Boolean.TRUE.equals(r) && Boolean.TRUE.equals(locked.invoke(provider))) {
                        return Boolean.TRUE;
                    }
                    return r;
                });
                ok.append("notificaciones ");
            }
        } catch (Throwable t) {
            ArtController.log("modo música: sin enganche de notificaciones: " + t);
        }

        try {
            Class<?> c = Class.forName(CLS_SMARTSPACE, false, cl);
            storedMediaTarget = c.getDeclaredField("storedMediaTarget");
            storedMediaTarget.setAccessible(true);
            updateMediaTarget = c.getDeclaredMethod("updateMediaTarget");
            x.hook(updateMediaTarget).intercept(chain -> {
                smartspace = chain.getThisObject();
                if (active && target != null) storedMediaTarget.set(smartspace, target);
                return chain.proceed();
            });
            for (java.lang.reflect.Constructor<?> k : c.getDeclaredConstructors()) {
                x.hook(k).intercept(chain -> {
                    Object r = chain.proceed();
                    smartspace = chain.getThisObject();
                    return r;
                });
            }
            ok.append("smartspace");
        } catch (Throwable t) {
            ArtController.log("modo música: sin enganche del smartspace: " + t);
        }
        try {
            Class<?> repo = Class.forName("com.android.systemui.keyguard.data.repository.KeyguardClockRepositoryImpl", false, cl);
            final Object small = Class.forName("com.android.systemui.keyguard.shared.model.ClockSizeSetting", false, cl)
                    .getField("SMALL").get(null);
            x.hook(repo.getDeclaredMethod("getClockSize")).intercept(chain -> forceSmallClock ? small : chain.proceed());
            // El observador del ajuste se registra al arrancar SystemUI; se captura para poder dispararlo.
            for (Method m : ContentResolver.class.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (!m.getName().startsWith("registerContentObserver") || p.length < 3 || p[0] != Uri.class
                        || p[2] != ContentObserver.class) continue;
                x.hook(m).intercept(chain -> {
                    Object r = chain.proceed();
                    Object uri = chain.getArg(0);
                    if (uri != null && uri.toString().endsWith("/" + CLOCK_KEY)) {
                        clockObserver = (ContentObserver) chain.getArg(2);
                    }
                    return r;
                });
            }
            ok.append(" reloj");
        } catch (Throwable t) {
            ArtController.log("modo música: sin enganche del reloj: " + t);
        }
        try {
            // AmbientDisplayConfiguration es del framework; DozeParameters lo consulta en onTuningChanged y avisa a todo
            // SystemUI (dispatchAlwaysOnEvent). Así el AOD se activa en memoria sin escribir doze_always_on.
            Class<?> adc = Class.forName("android.hardware.display.AmbientDisplayConfiguration", false, cl);
            x.hook(adc.getDeclaredMethod("alwaysOnEnabled", int.class))
                    .intercept(chain -> forceAod ? Boolean.TRUE : chain.proceed());
            Class<?> dp = Class.forName("com.android.systemui.statusbar.phone.DozeParameters", false, cl);
            onTuningChanged = dp.getDeclaredMethod("onTuningChanged", String.class, String.class);
            x.hook(dp.getDeclaredMethod("getAlwaysOn")).intercept(chain -> {
                dozeParameters = chain.getThisObject();
                return chain.proceed();
            });
            Class<?> machine = Class.forName("com.android.systemui.doze.DozeMachine", false, cl);
            Class<?> state = Class.forName("com.android.systemui.doze.DozeMachine$State", false, cl);
            stateDoze = state.getField("DOZE").get(null);
            stateAod = state.getField("DOZE_AOD").get(null);
            requestState = machine.getDeclaredMethod("requestState", state);
            getState = machine.getDeclaredMethod("getState");
            x.hook(getState).intercept(chain -> {
                dozeMachine = chain.getThisObject();
                return chain.proceed();
            });
            x.hook(requestState).intercept(chain -> {
                dozeMachine = chain.getThisObject();
                return chain.proceed();
            });
            ok.append(" aod");
        } catch (Throwable t) {
            ArtController.log("modo música: sin enganche de pantalla ambiente: " + t);
        }
        info = ok.length() == 0 ? "sin enganches" : ok.toString().trim();
    }

    /** Lo llama ArtController (hilo principal) cada vez que cambia la sesión, su estado o los ajustes. */
    void update(Context ctx, boolean on, String pkg, String title, String artist) {
        boolean was = active;
        active = on;
        if (on) {
            String key = pkg + "|" + title + "|" + artist;
            if (!key.equals(targetKey)) {
                targetKey = key;
                target = buildTarget(ctx, pkg, title, artist);
            }
        } else {
            target = null;
            targetKey = null;
        }
        if (was != on) {
            writeState(on);
            refreshNotifications();
            ArtController.log("modo música " + (on ? "activo" : "inactivo") + ": smartspace "
                    + (smartspace != null ? "capturado" : "sin capturar") + ", tarjeta " + (target != null ? "lista" : "sin crear")
                    + ", notificaciones " + (notifProvider != null ? "capturadas" : "sin capturar")
                    + ", reloj " + (clockObserver != null ? "capturado" : "sin capturar"));
        }
        refreshSmartspace();
    }

    private boolean readState() {
        try {
            if (composeState != null) return Boolean.TRUE.equals(stateGet.invoke(composeState));
        } catch (Throwable ignored) {
        }
        return active;
    }

    private void writeState(boolean on) {
        try {
            if (composeState != null) stateSet.invoke(composeState, on);
        } catch (Throwable t) {
            ArtController.log("modo música: no se pudo avisar a Compose: " + t);
        }
    }

    private void refreshNotifications() {
        try {
            if (notifProvider != null) notifyStateChanged.invoke(notifProvider, "LockArt modo música");
        } catch (Throwable t) {
            ArtController.log("modo música: no se pudo refiltrar notificaciones: " + t);
        }
    }

    private void refreshSmartspace() {
        try {
            if (smartspace == null) return;
            storedMediaTarget.set(smartspace, active ? target : null);
            updateMediaTarget.invoke(smartspace);
        } catch (Throwable t) {
            ArtController.log("modo música: no se pudo actualizar el smartspace: " + t);
        }
    }

    /** Igual que KeyguardMediaViewController: tipo 41, acción de cabecera con título, artista e icono. */
    private static Object buildTarget(Context ctx, String pkg, String title, String artist) {
        if (title == null || title.isEmpty()) return null;
        try {
            Class<?> actionCls = Class.forName("android.app.smartspace.SmartspaceAction");
            Class<?> ab = Class.forName("android.app.smartspace.SmartspaceAction$Builder");
            Object a = ab.getConstructor(String.class, String.class).newInstance("deviceMediaTitle", title);
            if (artist != null) ab.getMethod("setSubtitle", CharSequence.class).invoke(a, artist);
            Icon icon = appIcon(ctx, pkg);
            if (icon != null) ab.getMethod("setIcon", Icon.class).invoke(a, icon);
            Object action = ab.getMethod("build").invoke(a);

            Class<?> tb = Class.forName("android.app.smartspace.SmartspaceTarget$Builder");
            Object b = tb.getConstructor(String.class, ComponentName.class, android.os.UserHandle.class)
                    .newInstance("deviceMedia", MEDIA_COMPONENT, Process.myUserHandle());
            tb.getMethod("setFeatureType", int.class).invoke(b, 41);
            tb.getMethod("setHeaderAction", actionCls).invoke(b, action);
            return tb.getMethod("build").invoke(b);
        } catch (Throwable t) {
            ArtController.log("modo música: no se pudo crear la tarjeta del smartspace: " + t);
            return null;
        }
    }

    /** La capa monocroma del icono adaptativo (la de los iconos temáticos), en blanco; si no hay, el icono normal. */
    private static Icon appIcon(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            Drawable d = pm.getApplicationIcon(pkg);
            boolean mono = false;
            if (d instanceof AdaptiveIconDrawable && ((AdaptiveIconDrawable) d).getMonochrome() != null) {
                d = ((AdaptiveIconDrawable) d).getMonochrome();
                mono = true;
            }
            int size = Math.round(48 * ctx.getResources().getDisplayMetrics().density);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            if (mono) {
                // La capa monocroma trae márgenes de icono adaptativo (72 de 108 dp visibles): se amplía al recortar.
                int pad = Math.round(size * 0.25f);
                d.setBounds(-pad, -pad, size + pad, size + pad);
                d.setTint(0xFFFFFFFF);
                d.setTintMode(PorterDuff.Mode.SRC_IN);
            } else {
                d.setBounds(0, 0, size, size);
            }
            d.draw(c);
            return Icon.createWithBitmap(bmp);
        } catch (Throwable t) {
            Log.w("LockArt", "sin icono para " + pkg + ": " + t);
            return null;
        }
    }
}
