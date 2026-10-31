package com.elcesarmaat.lockart;

import android.content.Intent;
import android.os.Bundle;

/**
 * Letra sincronizada de una canción, tal como viaja de Spotify a SystemUI (solo arrays, sin clases propias en el
 * Bundle, para que se desempaquete sin el classloader del módulo).
 *
 * Las palabras de la línea i son [wordFrom[i], wordFrom[i + 1]) en los arrays de palabras; wChars es cuántos
 * caracteres de text[i] ocupa cada palabra, en orden. Una línea sin tiempos por palabra tiene ese rango vacío.
 */
final class Lyrics {

    static final String EXTRA_TRACK = "track";
    private static final String EXTRA_TEXT = "text";
    private static final String EXTRA_START = "start";
    private static final String EXTRA_END = "end";
    private static final String EXTRA_WORD_FROM = "word_from";
    private static final String EXTRA_WORD_START = "word_start";
    private static final String EXTRA_WORD_END = "word_end";
    private static final String EXTRA_WORD_CHARS = "word_chars";

    final String track;
    final String[] text;
    final long[] start;
    final long[] end;
    final int[] wordFrom;
    final long[] wStart;
    final long[] wEnd;
    final int[] wChars;

    Lyrics(String track, String[] text, long[] start, long[] end, int[] wordFrom, long[] wStart, long[] wEnd,
           int[] wChars) {
        this.track = track;
        this.text = text;
        this.start = start;
        this.end = end;
        this.wordFrom = wordFrom;
        this.wStart = wStart;
        this.wEnd = wEnd;
        this.wChars = wChars;
    }

    int size() {
        return text.length;
    }

    boolean hasWords(int line) {
        return wordFrom[line + 1] > wordFrom[line];
    }

    /** Última línea que ya empezó en esa posición; -1 antes de la primera. */
    int indexAt(long position) {
        int lo = 0, hi = text.length - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (start[mid] <= position) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    /**
     * Cuántos caracteres de la línea se han cantado ya, con fracción dentro de la palabra actual. Sin tiempos por
     * palabra devuelve la línea completa.
     */
    float sungChars(int line, long position) {
        if (!hasWords(line)) return text[line].length();
        float chars = 0f;
        for (int w = wordFrom[line]; w < wordFrom[line + 1]; w++) {
            if (position >= wEnd[w]) {
                chars += wChars[w];
            } else {
                if (position > wStart[w] && wEnd[w] > wStart[w]) {
                    chars += wChars[w] * (position - wStart[w]) / (float) (wEnd[w] - wStart[w]);
                }
                break;
            }
        }
        return chars;
    }

    void toIntent(Intent i) {
        i.putExtra(EXTRA_TRACK, track);
        i.putExtra(EXTRA_TEXT, text);
        i.putExtra(EXTRA_START, start);
        i.putExtra(EXTRA_END, end);
        i.putExtra(EXTRA_WORD_FROM, wordFrom);
        i.putExtra(EXTRA_WORD_START, wStart);
        i.putExtra(EXTRA_WORD_END, wEnd);
        i.putExtra(EXTRA_WORD_CHARS, wChars);
    }

    /** null si el Bundle no trae una letra coherente. */
    static Lyrics fromBundle(Bundle b) {
        if (b == null) return null;
        String track = b.getString(EXTRA_TRACK);
        String[] text = b.getStringArray(EXTRA_TEXT);
        long[] start = b.getLongArray(EXTRA_START);
        long[] end = b.getLongArray(EXTRA_END);
        int[] wordFrom = b.getIntArray(EXTRA_WORD_FROM);
        long[] wStart = b.getLongArray(EXTRA_WORD_START);
        long[] wEnd = b.getLongArray(EXTRA_WORD_END);
        int[] wChars = b.getIntArray(EXTRA_WORD_CHARS);
        if (track == null || text == null || start == null || end == null || wordFrom == null || wStart == null
                || wEnd == null || wChars == null) {
            return null;
        }
        int n = text.length;
        int words = wStart.length;
        if (n == 0 || start.length != n || end.length != n || wordFrom.length != n + 1 || wEnd.length != words
                || wChars.length != words || wordFrom[n] != words) {
            return null;
        }
        return new Lyrics(track, text, start, end, wordFrom, wStart, wEnd, wChars);
    }
}
