package com.umutk.pulse.extras.keyboard;

/**
 * WM Keyboard's correction engine (its trie-guided fuzzy search and key proximity, MIT), built into Pulse's Rust library (native/kbdlm/src/wm,
 * libpulse_lm.so). {@link #AVAILABLE} is false when the library is not in the APK; the hybrid correction then works without it.
 */
final class WmFixNative {
    static final boolean AVAILABLE;
    static {
        boolean ok;
        try { System.loadLibrary("pulse_lm"); create("a 1", "a"); ok = true; } catch (Throwable t) { ok = false; }
        AVAILABLE = ok;
    }
    private WmFixNative() {}

    /** [list]: "word count" lines; [rows]: the layout's letter rows, joined by commas. 0 when it could not be built. */
    static native long create(String list, String rows);
    static native void free(long h);
    /** Up to [limit] candidates of [typed], best first, as "word\tscore\tedits" lines. */
    static native String search(long h, String typed, int limit);
}
