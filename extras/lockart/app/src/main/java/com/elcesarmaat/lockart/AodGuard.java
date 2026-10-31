package com.elcesarmaat.lockart;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

/**
 * Decide si la pantalla ambiente con letras debe estar encendida: hace falta que se quiera (opción activa y música
 * con letra sonando) y que el teléfono no esté guardado. Guardado = sensor de proximidad tapado o pantalla boca abajo
 * durante GRACE_MS; al destaparlo o darle la vuelta vuelve el AOD.
 *
 * Los sensores solo escuchan mientras se quiere el AOD y la pantalla está en reposo (AOD o apagada), así no gastan
 * nada con la pantalla encendida ni sin música. La proximidad se pide en su versión que despierta al sistema, para
 * enterarse al sacar el teléfono del bolsillo aunque el AOD ya esté apagado.
 */
final class AodGuard implements SensorEventListener {

    interface Target {
        void setAod(boolean on);
    }

    private static final long GRACE_MS = 1500;
    /** Gravedad en el eje Z (sale de la pantalla) por debajo de esto = boca abajo. */
    private static final float FACE_DOWN_Z = -7f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Target target;
    private SensorManager sensors;
    private Sensor proximity;
    private Sensor gravity;
    private boolean listening;

    private boolean wanted;
    private boolean dozing;
    private boolean near;
    private boolean faceDown;
    /** Estado ya aplicado tras el margen de GRACE_MS. */
    private boolean stowed;
    private boolean applied;

    private final Runnable settle = this::settle;

    AodGuard(Target target) {
        this.target = target;
    }

    void init(Context c) {
        sensors = c.getSystemService(SensorManager.class);
        if (sensors == null) return;
        proximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY, true);
        if (proximity == null) proximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (gravity == null) gravity = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /** Opción activa y música con letra sonando. */
    void setWanted(boolean on) {
        if (on == wanted) return;
        wanted = on;
        if (!on) {
            near = false;
            faceDown = false;
            stowed = false;
        }
        update();
    }

    /** Pantalla en reposo (KeyguardState AOD o DOZING). */
    void setDozing(boolean on) {
        if (on == dozing) return;
        dozing = on;
        if (!on) {
            near = false;
            faceDown = false;
            stowed = false;
        }
        update();
    }

    boolean isStowed() {
        return stowed;
    }

    void stop() {
        wanted = false;
        dozing = false;
        main.removeCallbacksAndMessages(null);
        listen(false);
    }

    private void update() {
        listen(wanted && dozing);
        boolean on = wanted && !stowed;
        if (on != applied) {
            applied = on;
            target.setAod(on);
        }
    }

    private void listen(boolean on) {
        if (on == listening || sensors == null) return;
        listening = on;
        if (on) {
            if (proximity != null) sensors.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL, main);
            if (gravity != null) {
                sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_NORMAL, 500000, main);
            }
        } else {
            sensors.unregisterListener(this);
            main.removeCallbacks(settle);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        boolean before = near || faceDown;
        if (e.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            near = e.values[0] < Math.min(5f, e.sensor.getMaximumRange());
        } else {
            faceDown = e.values[2] < FACE_DOWN_Z;
        }
        if ((near || faceDown) != before) {
            main.removeCallbacks(settle);
            // Al guardarlo se espera un poco (para no apagar por un roce); al sacarlo se reacciona enseguida.
            main.postDelayed(settle, near || faceDown ? GRACE_MS : 200);
        }
    }

    private void settle() {
        boolean now = near || faceDown;
        if (now == stowed) return;
        stowed = now;
        ArtController.log("pantalla ambiente: teléfono " + (now ? (near ? "en el bolsillo" : "boca abajo") : "a la vista"));
        update();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
