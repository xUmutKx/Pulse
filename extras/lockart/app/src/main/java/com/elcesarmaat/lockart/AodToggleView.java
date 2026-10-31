package com.elcesarmaat.lockart;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Interruptor discreto del bloqueo para las letras en pantalla ambiente: una luna (rellena = activas, contorno =
 * apagadas) a la derecha, justo encima de la franja de letras. Ocupa toda la pantalla para posicionarse con el mismo
 * cálculo que LyricsView, pero solo acepta toques dentro de su círculo; el resto pasa a la escena de debajo.
 * Al tocarlo vibra un poco y muestra una etiqueta corta que se desvanece sola.
 */
final class AodToggleView extends View {

    /** La parte superior de la franja de letras, igual que LyricsView. */
    private static final float BAND_TOP = 0.565f;
    private static final long LABEL_MS = 1600;

    private final Runnable onToggle;
    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path moon = new Path();
    private final ValueAnimator labelFade = ValueAnimator.ofFloat(1f, 0f);
    private boolean on;
    private boolean dark;
    private String labelText;
    private float cx;
    private float cy;
    private float radius;

    AodToggleView(Context c, Runnable onToggle) {
        super(c);
        this.onToggle = onToggle;
        dp = c.getResources().getDisplayMetrics().density;
        radius = 18 * dp;
        glyph.setStrokeWidth(1.6f * dp);
        glyph.setStrokeJoin(Paint.Join.ROUND);
        label.setTypeface(Typeface.create(Typeface.create("google-sans-flex", Typeface.NORMAL), 500, false));
        label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13, c.getResources().getDisplayMetrics()));
        label.setTextAlign(Paint.Align.RIGHT);
        labelFade.setStartDelay(LABEL_MS);
        labelFade.setDuration(400);
        labelFade.addUpdateListener(a -> invalidate());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setOn(boolean value) {
        if (value == on) return;
        on = value;
        invalidate();
    }

    /** Colores oscuros cuando el velo del bloqueo es claro. */
    void setDark(boolean value) {
        dark = value;
        invalidate();
    }

    void showLabel(String text) {
        labelText = text;
        labelFade.cancel();
        labelFade.setCurrentFraction(0f);
        labelFade.start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        cx = getWidth() - 28 * dp - radius;
        cy = getHeight() * BAND_TOP - 30 * dp - radius;
        int ink = dark ? 0xFF1A1815 : Color.WHITE;

        fill.setColor((ink & 0x00FFFFFF) | ((on ? 0x33 : 0x1F) << 24));
        canvas.drawCircle(cx, cy, radius, fill);

        // Media luna: un círculo menos otro desplazado.
        float r = 7.5f * dp;
        moon.reset();
        moon.addCircle(cx, cy, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + 4.2f * dp, cy - 3.2f * dp, r * 0.86f, Path.Direction.CW);
        moon.op(bite, Path.Op.DIFFERENCE);
        glyph.setColor((ink & 0x00FFFFFF) | ((on ? 0xF2 : 0xA6) << 24));
        glyph.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
        canvas.drawPath(moon, glyph);

        Object v = labelFade.getAnimatedValue();
        float a = labelText == null ? 0f : (v instanceof Float ? (Float) v : 0f);
        if (a > 0f) {
            label.setColor((ink & 0x00FFFFFF) | (Math.round(0xE6 * a) << 24));
            label.setShadowLayer(dark ? 0f : 4 * dp, 0f, dark ? 0f : dp, (Math.round(0x66 * a) << 24));
            canvas.drawText(labelText, cx - radius - 10 * dp, cy + label.getTextSize() * 0.35f, label);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float dx = e.getX() - cx, dy = e.getY() - cy;
            float hit = radius + 8 * dp;
            if (getAlpha() < 0.5f || dx * dx + dy * dy > hit * hit) return false;
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            onToggle.run();
        }
        return true;
    }
}
