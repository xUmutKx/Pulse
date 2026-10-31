package com.elcesarmaat.lockart;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * Letra sincronizada en el bloqueo, dibujada a mano (sin TextViews) para rellenar la línea actual palabra por
 * palabra: lo no cantado va atenuado y lo cantado a color completo, interpolando dentro de la palabra.
 *
 * Al cambiar de línea hay cuatro animaciones (ANIM_*). La de serie, «Subir», mueve todo como una lista: la línea que
 * termina sube y se desvanece, y la siguiente, que ya se veía atenuada debajo, sube a su sitio creciendo y
 * aclarándose, así la vista no pierde el hilo. Los saltos (adelantar o retroceder) usan fundido.
 *
 * Solo se redibuja en cada fotograma mientras suena; con el bloqueo oculto (alfa 0) el bucle se detiene solo, y en
 * pantalla ambiente solo se redibuja al cambiar de línea y sin animación.
 * La app usa esta misma vista para la vista previa de las animaciones.
 */
final class LyricsView extends View {

    /** Posición de reproducción actual (ms) y si avanza, sin llamadas binder: la calcula ArtController. */
    interface Clock {
        long position();

        boolean playing();
    }

    static final int ANIM_SCROLL = 0;
    static final int ANIM_FADE = 1;
    static final int ANIM_ZOOM = 2;
    static final int ANIM_NONE = 3;

    /** Parte superior de la franja en el bloqueo, como fracción del alto de la pantalla. */
    static final float BAND_TOP = 0.565f;
    private static final float DIM = 0.42f;
    private static final float NEXT = 0.38f;
    private static final float CURRENT_SP = 25f;
    private static final float NEXT_SP = 18f;
    /** Tras la última línea se deja de mostrar la letra pasado este margen. */
    private static final long TAIL_MS = 1500;

    private final Clock clock;
    private final TextPaint current = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint next = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final float dp;
    private final ValueAnimator enter = ValueAnimator.ofFloat(0f, 1f);

    private Lyrics lyrics;
    private int index = -2;
    private int layoutWidth;
    private StaticLayout currentLayout;
    private StaticLayout nextLayout;
    /** La línea que acaba de terminar, para la animación de salida. */
    private StaticLayout previousLayout;
    /** El cambio de línea en curso es un avance de una línea (no un salto). */
    private boolean stepForward;
    private int animation = ANIM_SCROLL;
    private float bandTop = BAND_TOP;
    private int color = Color.WHITE;
    private boolean darkVeil;
    /** 0 = bloqueo, 1 = pantalla ambiente (sin sombra, más tenue y con desplazamiento contra el quemado). */
    private float ambient;

    LyricsView(Context c, Clock clock) {
        super(c);
        this.clock = clock;
        dp = c.getResources().getDisplayMetrics().density;
        Typeface flex = Typeface.create("google-sans-flex", Typeface.NORMAL);
        current.setTypeface(Typeface.create(flex, 600, false));
        next.setTypeface(Typeface.create(flex, 500, false));
        setTextScale(1f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        enter.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        enter.addUpdateListener(a -> invalidate());
        setDark(false);
    }

    void setLyrics(Lyrics l) {
        if (l == lyrics) return;
        lyrics = l;
        resetLayouts();
    }

    boolean hasLyrics() {
        return lyrics != null;
    }

    void setAnimation(int mode) {
        animation = mode;
    }

    /** Para la vista previa de la app: dónde empieza la franja dentro de la vista. */
    void setBandTop(float fraction) {
        bandTop = fraction;
        invalidate();
    }

    /** Para la vista previa de la app: letra algo más pequeña que en el bloqueo. */
    void setTextScale(float scale) {
        current.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, CURRENT_SP * scale,
                getResources().getDisplayMetrics()));
        next.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, NEXT_SP * scale,
                getResources().getDisplayMetrics()));
        resetLayouts();
    }

    private void resetLayouts() {
        index = -2;
        currentLayout = null;
        nextLayout = null;
        previousLayout = null;
        enter.cancel();
        invalidate();
    }

    void setAmbient(float a) {
        if (a == ambient) return;
        ambient = a;
        setDark(darkVeil);
    }

    /** Texto oscuro cuando el velo es blanco (fondo de bloqueo que pide texto oscuro). */
    void setDark(boolean veilLight) {
        darkVeil = veilLight;
        // En AOD el fondo es negro: texto blanco atenuado aunque el velo del bloqueo sea claro.
        boolean dark = veilLight && ambient < 0.5f;
        color = dark ? 0xFF1A1815 : withAlpha(Color.WHITE, 1f - 0.3f * ambient);
        float r = dark ? 0f : 6 * dp;
        current.setShadowLayer(r, 0f, dark ? 0f : dp, 0x73000000);
        next.setShadowLayer(r, 0f, dark ? 0f : dp, 0x59000000);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Lyrics l = lyrics;
        if (l == null) return;
        long pos = clock.position();
        int i = l.indexAt(pos);
        if (i >= 0 && i == l.size() - 1 && pos > l.end[i] + TAIL_MS) i = -3;
        int pad = Math.round(28 * dp);
        int w = getWidth() - 2 * pad;
        if (w <= 0) return;
        if (i != index || w != layoutWidth) relayout(l, i, w, i != index && index != -2);

        Object v = enter.getAnimatedValue();
        float t = enter.isRunning() && v instanceof Float ? (Float) v : 1f;
        canvas.save();
        // Contra el quemado en AOD: se desplaza unos dp cada minuto.
        long minute = System.currentTimeMillis() / 60000L;
        float shift = ambient * 3 * dp;
        canvas.translate(pad + shift * ((minute % 5) - 2), getHeight() * bandTop + shift * (((minute / 5) % 3) - 1));
        int mode = t >= 1f ? ANIM_NONE : (animation == ANIM_SCROLL && !stepForward ? ANIM_FADE : animation);
        switch (mode) {
            case ANIM_SCROLL:
                drawScroll(canvas, l, i, pos, w, t);
                break;
            case ANIM_ZOOM:
                drawZoom(canvas, l, i, pos, w, t);
                break;
            case ANIM_FADE:
                drawFade(canvas, l, i, pos, t);
                break;
            default:
                drawStill(canvas, l, i, pos);
                break;
        }
        canvas.restore();

        if (!clock.playing() || getAlpha() <= 0f || !isShown()) return;
        if (ambient < 0.5f) {
            postInvalidateOnAnimation();
            return;
        }
        // En AOD no hay relleno por palabra: solo se redibuja cuando empieza la línea siguiente (y al menos cada
        // minuto, para el desplazamiento contra el quemado).
        int nextIndex = i + 1;
        long wait = nextIndex >= 0 && nextIndex < l.size() ? l.start[nextIndex] - pos : 60000L;
        postInvalidateDelayed(Math.max(50L, Math.min(wait, 60000L)));
    }

    private float gap() {
        return 10 * dp;
    }

    /** Sin animación: línea actual y, debajo, la siguiente atenuada. */
    private void drawStill(Canvas canvas, Lyrics l, int i, long pos) {
        if (currentLayout != null) {
            drawSung(canvas, l, i, pos, l.hasWords(i) ? DIM : 1f, 1f);
            canvas.translate(0, currentLayout.getHeight() + gap());
        }
        drawNext(canvas, currentLayout == null ? DIM : NEXT);
    }

    /**
     * «Subir»: todo se mueve hacia arriba lo que medía la línea saliente. La entrante parte de donde estaba como
     * «siguiente» (tamaño y opacidad de siguiente) y llega a su sitio como línea actual.
     */
    private void drawScroll(Canvas canvas, Lyrics l, int i, long pos, int w, float t) {
        float up = previousLayout.getHeight() + gap();
        canvas.save();
        canvas.translate(0, -up * t);
        current.setColor(withAlpha(color, 1f - t));
        previousLayout.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.translate(0, up * (1f - t));
        if (currentLayout != null) {
            float from = next.getTextSize() / current.getTextSize();
            float s = from + (1f - from) * t;
            canvas.save();
            canvas.scale(s, s, w / 2f, 0f);
            float base = l.hasWords(i) ? DIM : 1f;
            drawSung(canvas, l, i, pos, NEXT + (base - NEXT) * t, t);
            canvas.restore();
            canvas.translate(0, currentLayout.getHeight() * s + gap());
        }
        // La nueva siguiente asoma desde abajo.
        canvas.translate(0, up * 0.5f * (1f - t));
        drawNext(canvas, NEXT * t);
        canvas.restore();
    }

    /** «Fundido»: la saliente se apaga rápido y la entrante aparece subiendo un poco. */
    private void drawFade(Canvas canvas, Lyrics l, int i, long pos, float t) {
        if (previousLayout != null) {
            current.setColor(withAlpha(color, Math.max(0f, 1f - 2f * t)));
            previousLayout.draw(canvas);
        }
        canvas.translate(0, (1f - t) * 14 * dp);
        if (currentLayout != null) {
            drawSung(canvas, l, i, pos, (l.hasWords(i) ? DIM : 1f) * t, t);
            canvas.translate(0, currentLayout.getHeight() + gap());
        }
        drawNext(canvas, (currentLayout == null ? DIM : NEXT) * t);
    }

    /** «Zoom»: la saliente se agranda y se apaga; la entrante crece desde un poco más pequeña. */
    private void drawZoom(Canvas canvas, Lyrics l, int i, long pos, int w, float t) {
        if (previousLayout != null) {
            float s = 1f + 0.08f * t;
            canvas.save();
            canvas.scale(s, s, w / 2f, previousLayout.getHeight() / 2f);
            current.setColor(withAlpha(color, Math.max(0f, 1f - 1.6f * t)));
            previousLayout.draw(canvas);
            canvas.restore();
        }
        if (currentLayout != null) {
            float s = 0.88f + 0.12f * t;
            canvas.save();
            canvas.scale(s, s, w / 2f, currentLayout.getHeight() / 2f);
            drawSung(canvas, l, i, pos, (l.hasWords(i) ? DIM : 1f) * t, t);
            canvas.restore();
            canvas.translate(0, currentLayout.getHeight() + gap());
        }
        drawNext(canvas, (currentLayout == null ? DIM : NEXT) * t);
    }

    private void drawNext(Canvas canvas, float alpha) {
        if (nextLayout == null) return;
        next.setColor(withAlpha(color, alpha));
        nextLayout.draw(canvas);
    }

    /**
     * Línea actual: entera con opacidad base (atenuada si tiene tiempos por palabra) y, recortada a lo ya cantado,
     * con opacidad sung.
     */
    private void drawSung(Canvas canvas, Lyrics l, int i, long pos, float base, float sungAlpha) {
        StaticLayout layout = currentLayout;
        current.setColor(withAlpha(color, base));
        layout.draw(canvas);
        if (!l.hasWords(i)) return;

        float sung = l.sungChars(i, pos);
        int whole = (int) sung;
        float frac = sung - whole;
        current.setColor(withAlpha(color, sungAlpha));
        for (int line = 0; line < layout.getLineCount(); line++) {
            int ls = layout.getLineStart(line);
            int le = layout.getLineEnd(line);
            if (whole < ls) break;
            float left = layout.getLineLeft(line);
            float right;
            if (whole >= le) {
                right = layout.getLineRight(line);
            } else {
                float a = layout.getPrimaryHorizontal(whole);
                float b = whole + 1 == le ? layout.getLineRight(line) : layout.getPrimaryHorizontal(whole + 1);
                right = a + (b - a) * frac;
            }
            canvas.save();
            canvas.clipRect(left, layout.getLineTop(line), right, layout.getLineBottom(line));
            layout.draw(canvas);
            canvas.restore();
        }
    }

    private void relayout(Lyrics l, int i, int w, boolean animate) {
        boolean sameWidth = w == layoutWidth;
        stepForward = animate && sameWidth && index >= 0 && i == index + 1 && currentLayout != null;
        previousLayout = animate && sameWidth ? currentLayout : null;
        index = i;
        layoutWidth = w;
        currentLayout = i >= 0 ? build(l.text[i], current, w, 3) : null;
        int n = i == -3 ? -1 : i + 1;
        nextLayout = n >= 0 && n < l.size() ? build(l.text[n], next, w, 1) : null;
        enter.cancel();
        // En pantalla ambiente no se anima: allí solo se redibuja al cambiar de línea.
        if (animate && animation != ANIM_NONE && ambient < 0.5f) {
            enter.setDuration(animation == ANIM_SCROLL && stepForward ? 460 : animation == ANIM_ZOOM ? 340 : 280);
            enter.start();
        } else {
            previousLayout = null;
        }
    }

    private static StaticLayout build(String text, TextPaint paint, int width, int maxLines) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.08f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build();
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * Math.max(0f, Math.min(1f, alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
