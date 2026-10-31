package com.elcesarmaat.lockart;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector mínimo de la respuesta protobuf de color-lyrics/v2. Los números de campo vienen del SpotifyProtoParser de
 * LyricProvider (tomakino, Apache-2.0):
 *
 *   respuesta: 1 = lyrics
 *   lyrics:    1 = syncType (varint), 2 = líneas (repetido)
 *   línea:     1 = startTimeMs (varint), 2 = texto, 3 = sílabas (repetido)
 *   sílaba:    1 = startTimeMs, 2 = número de caracteres, 3 = endTimeMs
 *
 * Cada línea termina donde empieza la siguiente (también si la siguiente está vacía, que marca una pausa); las
 * líneas vacías no se muestran.
 */
final class SpotifyProto {

    private static final long LAST_LINE_MS = 5000;

    private SpotifyProto() {
    }

    /** null si no es protobuf válido, no trae líneas o no está sincronizada (todas en 0). */
    static Lyrics parse(String track, byte[] body) {
        try {
            if (body == null || body.length == 0) return null;
            if (body[0] == '{') return parseJson(track, body);
            byte[] lyrics = null;
            Reader r = new Reader(body, 0, body.length);
            while (r.more()) {
                int tag = (int) r.varint();
                if (tag >>> 3 == 1 && (tag & 7) == 2) lyrics = r.bytes();
                else r.skip(tag & 7);
            }
            if (lyrics == null) return null;

            List<long[]> starts = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            List<List<long[]>> syllables = new ArrayList<>();
            r = new Reader(lyrics, 0, lyrics.length);
            while (r.more()) {
                int tag = (int) r.varint();
                if (tag >>> 3 == 2 && (tag & 7) == 2) {
                    byte[] line = r.bytes();
                    long startMs = 0;
                    String text = "";
                    List<long[]> syl = new ArrayList<>();
                    Reader lr = new Reader(line, 0, line.length);
                    while (lr.more()) {
                        int t = (int) lr.varint();
                        int field = t >>> 3;
                        if (field == 1 && (t & 7) == 0) startMs = lr.varint();
                        else if (field == 2 && (t & 7) == 2) text = new String(lr.bytes(), StandardCharsets.UTF_8);
                        else if (field == 3 && (t & 7) == 2) syl.add(syllable(lr.bytes()));
                        else lr.skip(t & 7);
                    }
                    starts.add(new long[]{startMs});
                    texts.add(text);
                    syllables.add(syl);
                } else {
                    r.skip(tag & 7);
                }
            }
            return build(track, starts, texts, syllables);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Respaldo: a veces el servidor responde en JSON en vez de protobuf
     * ({"lyrics": {"lines": [{"startTimeMs": "1230", "words": "..."}]}}). Ahí solo hay tiempos por línea.
     */
    private static Lyrics parseJson(String track, byte[] body) {
        try {
            org.json.JSONObject root = new org.json.JSONObject(new String(body, StandardCharsets.UTF_8));
            org.json.JSONObject lyrics = root.optJSONObject("lyrics");
            org.json.JSONArray lines = lyrics == null ? null : lyrics.optJSONArray("lines");
            if (lines == null) return null;
            List<long[]> starts = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            List<List<long[]>> syllables = new ArrayList<>();
            for (int i = 0; i < lines.length(); i++) {
                org.json.JSONObject line = lines.optJSONObject(i);
                if (line == null) continue;
                // Los tiempos vienen como texto ("1230"); optLong los convierte.
                starts.add(new long[]{line.optLong("startTimeMs", 0)});
                texts.add(line.optString("words", ""));
                syllables.add(new ArrayList<>());
            }
            return build(track, starts, texts, syllables);
        } catch (Throwable t) {
            return null;
        }
    }

    private static long[] syllable(byte[] b) {
        long start = 0, count = 0, end = 0;
        Reader r = new Reader(b, 0, b.length);
        while (r.more()) {
            int t = (int) r.varint();
            int field = t >>> 3;
            if ((t & 7) != 0) {
                r.skip(t & 7);
                continue;
            }
            long v = r.varint();
            if (field == 1) start = v;
            else if (field == 2) count = v;
            else if (field == 3) end = v;
        }
        return new long[]{start, count, end};
    }

    private static Lyrics build(String track, List<long[]> starts, List<String> texts, List<List<long[]>> syllables) {
        int all = texts.size();
        boolean synced = false;
        for (long[] s : starts) synced |= s[0] > 0;
        if (all == 0 || !synced) return null;

        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < all; i++) if (!texts.get(i).trim().isEmpty()) keep.add(i);
        int n = keep.size();
        if (n == 0) return null;

        String[] text = new String[n];
        long[] start = new long[n];
        long[] end = new long[n];
        int[] wordFrom = new int[n + 1];
        List<long[]> words = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            int i = keep.get(k);
            text[k] = texts.get(i);
            start[k] = starts.get(i)[0];
            end[k] = i + 1 < all ? starts.get(i + 1)[0] : start[k] + LAST_LINE_MS;
            wordFrom[k] = words.size();
            // Solo se usan las sílabas si cubren el texto exacto; si no, la línea va sin tiempos por palabra.
            List<long[]> syl = syllables.get(i);
            long total = 0;
            for (long[] s : syl) total += s[1];
            if (!syl.isEmpty() && total == text[k].length()) words.addAll(syl);
        }
        wordFrom[n] = words.size();
        long[] wStart = new long[words.size()];
        long[] wEnd = new long[words.size()];
        int[] wChars = new int[words.size()];
        for (int w = 0; w < words.size(); w++) {
            wStart[w] = words.get(w)[0];
            wChars[w] = (int) words.get(w)[1];
            wEnd[w] = Math.max(words.get(w)[2], wStart[w]);
        }
        return new Lyrics(track, text, start, end, wordFrom, wStart, wEnd, wChars);
    }

    /** Lectura secuencial de un mensaje protobuf (varint, 64 bits, delimitado y 32 bits). */
    private static final class Reader {
        private final byte[] b;
        private int pos;
        private final int limit;

        Reader(byte[] b, int pos, int limit) {
            this.b = b;
            this.pos = pos;
            this.limit = limit;
        }

        boolean more() {
            return pos < limit;
        }

        long varint() {
            long v = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= limit) throw new IllegalArgumentException("varint truncado");
                int x = b[pos++] & 0xff;
                v |= (long) (x & 0x7f) << shift;
                if ((x & 0x80) == 0) return v;
            }
            throw new IllegalArgumentException("varint mal formado");
        }

        byte[] bytes() {
            long len = varint();
            if (len < 0 || len > limit - pos) throw new IllegalArgumentException("campo truncado");
            byte[] out = new byte[(int) len];
            System.arraycopy(b, pos, out, 0, (int) len);
            pos += (int) len;
            return out;
        }

        void skip(int wire) {
            switch (wire) {
                case 0:
                    varint();
                    break;
                case 1:
                    pos += 8;
                    break;
                case 2:
                    bytes();
                    break;
                case 5:
                    pos += 4;
                    break;
                default:
                    throw new IllegalArgumentException("tipo de campo " + wire);
            }
            if (pos > limit) throw new IllegalArgumentException("campo truncado");
        }
    }
}
