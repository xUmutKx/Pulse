package com.umutk.pulse.extras.keyboard;

/**
 * The Rust build of the keyboard model (native/kbdlm, libpulse_lm.so): the same file format and the same maths as {@link CharLm}, about four times faster on a phone's CPU.
 * When the library is not in the APK (or does not load) {@link #AVAILABLE} is false and CharLm keeps using its own Java code.
 */
final class CharLmNative {
    static final boolean AVAILABLE;
    static {
        boolean ok;
        try { System.loadLibrary("pulse_lm"); ok = true; } catch (Throwable t) { ok = false; }
        AVAILABLE = ok;
    }
    private CharLmNative() {}

    static native long load(byte[] data);
    static native void free(long h);
    static native boolean cased(long h);
    static native float[] state(long h, String text);
    static native float[] advance(long h, float[] st, String more);
    static native float score(long h, float[] ctx, String word, float floor);
    static native long stepNanos(long h);
}
