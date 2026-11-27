package com.umutk.pulse.features;

/**
 * The Rust file-name search (native/filesearch, libpulse_search.so): several threads walk the folders of the whole phone and stop when enough names matched. When the library is
 * not in the APK (or does not load) {@link #AVAILABLE} is false and {@link FileSearch} walks the folders in Kotlin.
 */
final class FileSearchNative {
    static final boolean AVAILABLE;
    static {
        boolean ok;
        try { System.loadLibrary("pulse_search"); ok = true; } catch (Throwable t) { ok = false; }
        AVAILABLE = ok;
    }
    private FileSearchNative() {}

    /** The paths whose last name contains [query] (case and the Turkish i ignored), at most [limit]. */
    static native String[] search(String root, String query, boolean hidden, int limit);

    /** One folder in one pass: "name\u0001kind(0 file, 1 folder)\u0001size\u0001modified ms" for each entry. */
    static native String[] list(String dir, boolean hidden);
}
