package com.umutk.pulse.extras.keyboard;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Pulse's own neural language model for the keyboard: a character-level GRU (one layer) trained on open Turkish and English text
 * (Leipzig Corpora Collection, CC BY). It reads the text before the cursor and tells how likely a word is to come next, letter by letter.
 * The keyboard asks it which of the near words fits the sentence, so "guzel" becomes "güzel" after "hava çok", and a real word that does
 * not fit the sentence is mended while a rare word that fits is left alone.
 *
 * File (assets/kb/lm_xx.bin, big-endian): "PLM1", V, H, length of the character set, the character set (UTF-8);
 * the input part of each gate for every character (V x 3H, half floats: the embedding times the input weights plus the input bias);
 * the hidden weights (3H x H, 8-bit, one scale per row), their scales and the hidden bias (3H floats);
 * the output weights (V x H, 8-bit, one scale per row), their scales and the output bias (V floats). Gate order r, z, n as in PyTorch.
 */
public final class CharLm {
    private final int v, h;
    private final String chars;
    private final int[] map = new int[0x500];
    private final int unk, space, start;
    private final float[] x;      // V x 3H
    private final byte[] w;       // 3H x H
    private final float[] ws, bh; // 3H
    private final byte[] o;       // V x H
    private final float[] os, bo; // V

    private CharLm(DataInputStream in) throws IOException {
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'P' || magic[1] != 'L' || magic[2] != 'M' || magic[3] != '1') throw new IOException("not a Pulse language model");
        v = in.readInt(); h = in.readInt();
        byte[] cs = new byte[in.readInt()];
        in.readFully(cs);
        chars = new String(cs, StandardCharsets.UTF_8);
        if (chars.length() != v) throw new IOException("character set does not match");
        unk = chars.indexOf('#'); space = chars.indexOf(' '); start = chars.indexOf('\n');
        for (int i = 0; i < map.length; i++) map[i] = unk;
        for (int i = 0; i < v; i++) { char c = chars.charAt(i); if (c < map.length) map[c] = i; }
        for (char c = '1'; c <= '9'; c++) map[c] = map['0'];
        int g = 3 * h;
        x = new float[v * g];
        for (int i = 0; i < x.length; i++) x[i] = half(in.readShort());
        w = new byte[g * h]; in.readFully(w);
        ws = floats(in, g); bh = floats(in, g);
        o = new byte[v * h]; in.readFully(o);
        os = floats(in, v); bo = floats(in, v);
    }

    public static CharLm load(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw, 1 << 16))) { return new CharLm(in); }
    }

    private static float[] floats(DataInputStream in, int n) throws IOException {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) a[i] = in.readFloat();
        return a;
    }

    /** IEEE half float to float. */
    private static float half(short s) {
        int b = s & 0xffff, e = (b >>> 10) & 0x1f, m = b & 0x3ff;
        float sign = (b & 0x8000) != 0 ? -1f : 1f;
        if (e == 0) return sign * m * 5.9604645e-8f;
        if (e == 31) return m == 0 ? sign * Float.POSITIVE_INFINITY : Float.NaN;
        return sign * Float.intBitsToFloat(((e - 15 + 127) << 23) | (m << 13));
    }

    /** The index of a character (already lower-cased the way the language does it); other characters are the unknown sign. */
    public int index(char c) {
        if (Character.isWhitespace(c)) return space;
        if (c == '’' || c == '`') c = '\'';
        if (c == '–' || c == '—') c = '-';
        if (c == '\u201C' || c == '\u201D' || c == '\u00AB' || c == '\u00BB') c = '"';
        return c < map.length ? map[c] : unk;
    }

    private static float sig(float a) { return 1f / (1f + (float) Math.exp(-a)); }

    /** One character in: the state [st] moves on (in place). [tmp] holds 3H floats. */
    private void step(float[] st, int c, float[] tmp) {
        int g = 3 * h;
        for (int r = 0; r < g; r++) {
            int base = r * h; float s = 0f;
            for (int j = 0; j < h; j++) s += w[base + j] * st[j];
            tmp[r] = s * ws[r] + bh[r];
        }
        int xb = c * g;
        for (int j = 0; j < h; j++) {
            float rr = sig(x[xb + j] + tmp[j]);
            float zz = sig(x[xb + h + j] + tmp[h + j]);
            float nn = (float) Math.tanh(x[xb + 2 * h + j] + rr * tmp[2 * h + j]);
            st[j] = (1f - zz) * nn + zz * st[j];
        }
    }

    /** log2 of the chance of character [c] after state [st]. [lg] holds V floats. */
    private float logp(float[] st, int c, float[] lg) {
        float max = -1e30f;
        for (int k = 0; k < v; k++) {
            int base = k * h; float s = 0f;
            for (int j = 0; j < h; j++) s += o[base + j] * st[j];
            lg[k] = s * os[k] + bo[k];
            if (lg[k] > max) max = lg[k];
        }
        double sum = 0;
        for (int k = 0; k < v; k++) sum += Math.exp(lg[k] - max);
        return (float) ((lg[c] - max - Math.log(sum)) / Math.log(2));
    }

    /** The state after the start of a text and [text] (the last 120 characters are enough). */
    public float[] state(CharSequence text) {
        float[] st = new float[h]; float[] tmp = new float[3 * h];
        step(st, start, tmp);
        int from = Math.max(0, text.length() - 120);
        for (int i = from; i < text.length(); i++) step(st, index(text.charAt(i)), tmp);
        return st;
    }

    /** log2 of the chance that [word] and then a space come after [ctx] (a state from [state]; it is not changed). */
    public float score(float[] ctx, CharSequence word) {
        float[] st = ctx.clone(); float[] tmp = new float[3 * h]; float[] lg = new float[v];
        float s = 0f;
        int n = word.length();
        for (int i = 0; i <= n; i++) {
            int c = i < n ? index(word.charAt(i)) : space;
            s += logp(st, c, lg);
            if (i < n) step(st, c, tmp);
        }
        return s;
    }
}
