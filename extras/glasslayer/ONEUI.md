# Glass Layer · One UI (Pulse edition)

A fork of Glass Layer 1.1.0 by DrDisagree (GPL-3.0, see LICENSE), changed for Samsung One UI phones.
It is built as its own APK (package `com.umutk.pulse.glasslayer`), separately from Pulse, and installs beside the original.

Changes from 1.1.0:

- **Its own package** (`com.umutk.pulse.glasslayer`) and its own broadcast actions, so it never clashes with the original module.
- **Performance**: Balanced (60 fps) is the default instead of Full (which followed a 120 Hz screen); the backdrop is captured at half size at most (it was up to three quarters);
  and while nothing moves (no finger down, no glass surface moving) the glass is caught only a few times a second (4 by default, set in the One UI page).
- **Apps**: besides Settings and the package installers, Samsung's My Files and Clock are in scope. Each app has its own switch in the One UI page; a switched-off app gets no hook at all.
- **One UI inspector** (off by default): when switched on, every window of System UI (shade, quick settings, volume panel, lock screen) and every screen of the apps above
  has its views written down (class, id, background, size). The One UI page saves them to `Download/GlassLayer`. One UI uses Samsung's own classes for these
  surfaces, not the AOSP / Pixel ones the glass hooks look for; the hooks for those classes are written from what the inspector found.
