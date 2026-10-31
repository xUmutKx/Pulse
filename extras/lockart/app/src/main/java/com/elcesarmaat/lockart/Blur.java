package com.elcesarmaat.lockart;

import android.graphics.Bitmap;

/**
 * Desenfoque estático: se calcula una sola vez por carátula (no cuesta GPU en cada fotograma como un RenderEffect).
 * Lo usan igual SystemUI y la vista previa de la app, así que se ven idénticos aunque la carátula llegue a distinta
 * resolución.
 *
 * El nivel fija el desenfoque (sigma) como fracción del lado corto de la carátula, con una curva para que los
 * primeros niveles sean muy sutiles. Los desenfoques pequeños usan una gaussiana exacta, que admite fracciones de
 * píxel; el filtro de caja no baja de radio 1 y por eso el nivel 1 se notaba tanto.
 */
final class Blur {

    /** Sigma del nivel máximo: 3,3 % del lado corto (lo que era el nivel 10 en la versión 2.0). */
    private static final float MAX_FRACTION = 0.0333f;
    private static final double CURVE = 1.4;
    /**
     * Se trabaja a la resolución justa para que sigma quede en unos 6 px: reducir más añadiría desenfoque propio al
     * ampliar, y reducir menos solo gastaría CPU.
     */
    private static final float WORK_SIGMA = 6f;
    /** Por debajo de esta sigma (en píxeles de trabajo) se usa la gaussiana exacta en vez de las tres cajas. */
    private static final float BOX_FROM = 2.5f;
    private static final int MIN_BASE = 48;
    private static final int MAX_BASE = 1080;

    private Blur() {
    }

    /** level 0 devuelve la misma carátula; 1..Config.MAX_BLUR desenfoca cada vez más. */
    static Bitmap apply(Bitmap src, int level) {
        if (src == null || level <= 0) return src;
        Bitmap soft = src.getConfig() == Bitmap.Config.HARDWARE ? src.copy(Bitmap.Config.ARGB_8888, false) : src;
        int shortSide = Math.min(soft.getWidth(), soft.getHeight());
        float fraction = MAX_FRACTION * (float) Math.pow(Math.min(level, Config.MAX_BLUR) / (float) Config.MAX_BLUR, CURVE);
        int base = Math.min(Math.min(shortSide, MAX_BASE), Math.max(MIN_BASE, Math.round(WORK_SIGMA / fraction)));
        float s = base / (float) shortSide;
        int w = Math.max(1, Math.round(soft.getWidth() * s));
        int h = Math.max(1, Math.round(soft.getHeight() * s));
        Bitmap work = (w == soft.getWidth() && h == soft.getHeight()) ? soft : Bitmap.createScaledBitmap(soft, w, h, true);
        float sigma = fraction * base;
        int[] a = new int[w * h];
        int[] b = new int[w * h];
        work.getPixels(a, 0, w, 0, 0, w, h);
        if (sigma < BOX_FROM) {
            float[] k = kernel(sigma);
            gauss(a, b, w, h, 1, w, k);
            gauss(b, a, h, w, w, 1, k);
        } else {
            // Tres pasadas de caja de radio r equivalen a una gaussiana de sigma = sqrt(r (r + 1)).
            int r = Math.max(1, Math.round((float) ((Math.sqrt(1 + 4 * sigma * sigma) - 1) / 2)));
            for (int pass = 0; pass < 3; pass++) {
                box(a, b, w, h, 1, w, r);
                box(b, a, h, w, w, 1, r);
            }
        }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888);
    }

    /** Pesos normalizados de una gaussiana de radio 3 sigma. */
    private static float[] kernel(float sigma) {
        int kr = Math.max(1, (int) Math.ceil(3 * sigma));
        float[] k = new float[2 * kr + 1];
        float sum = 0f;
        for (int i = -kr; i <= kr; i++) {
            k[i + kr] = (float) Math.exp(-(i * i) / (2.0 * sigma * sigma));
            sum += k[i + kr];
        }
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        return k;
    }

    /** Convolución con el núcleo k sobre las mismas líneas que box (filas: 1 y w; columnas: w y 1). */
    private static void gauss(int[] in, int[] out, int len, int lines, int step, int lineStep, float[] k) {
        int kr = k.length / 2;
        for (int l = 0; l < lines; l++) {
            int base = l * lineStep;
            for (int i = 0; i < len; i++) {
                float sa = 0f, sr = 0f, sg = 0f, sb = 0f;
                for (int j = -kr; j <= kr; j++) {
                    int p = in[base + edge(i + j, len) * step];
                    float wt = k[j + kr];
                    sa += (p >>> 24) * wt;
                    sr += ((p >> 16) & 0xff) * wt;
                    sg += ((p >> 8) & 0xff) * wt;
                    sb += (p & 0xff) * wt;
                }
                out[base + i * step] = (channel(sa) << 24) | (channel(sr) << 16) | (channel(sg) << 8) | channel(sb);
            }
        }
    }

    private static int channel(float v) {
        int c = Math.round(v);
        return c < 0 ? 0 : (c > 255 ? 255 : c);
    }

    /**
     * Media móvil de radio r sobre 'lines' líneas de 'len' píxeles; dentro de una línea los píxeles están separados
     * por 'step' y cada línea empieza 'lineStep' más allá de la anterior (filas: 1 y w; columnas: w y 1).
     */
    private static void box(int[] in, int[] out, int len, int lines, int step, int lineStep, int r) {
        int div = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int base = l * lineStep;
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[base + edge(i, len) * step];
                sa += p >>> 24;
                sr += (p >> 16) & 0xff;
                sg += (p >> 8) & 0xff;
                sb += p & 0xff;
            }
            for (int i = 0; i < len; i++) {
                out[base + i * step] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int pin = in[base + edge(i + r + 1, len) * step];
                int pout = in[base + edge(i - r, len) * step];
                sa += (pin >>> 24) - (pout >>> 24);
                sr += ((pin >> 16) & 0xff) - ((pout >> 16) & 0xff);
                sg += ((pin >> 8) & 0xff) - ((pout >> 8) & 0xff);
                sb += (pin & 0xff) - (pout & 0xff);
            }
        }
    }

    private static int edge(int i, int len) {
        return i < 0 ? 0 : (i >= len ? len - 1 : i);
    }
}
