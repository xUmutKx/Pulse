-dontwarn okio.**
-dontwarn com.android.apksig.**
-keep class com.android.apksig.** { *; }

-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-dontwarn rikka.sui.**

-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.junrar.**
-dontwarn org.slf4j.**
-dontwarn org.brotli.**
-dontwarn com.github.luben.**
-dontwarn org.objectweb.asm.**

# Klinker MMS library (Messages attachments): its HTTP path needs okhttp 2.x, which is left out on purpose; the system MMS stack is used, so these classes are never called
-dontwarn com.squareup.okhttp.**

# android-smsmms ships its own copies of android.net.LinkProperties and friends; R8 renamed them and inlined their private fields into Hw.kt (VerifyError). Keep the names so the platform classes are used.
-keep class android.net.LinkAddress { *; }
-keep class android.net.LinkProperties { *; }
-keep class android.net.LinkProperties$1 { *; }
-keep class android.net.RouteInfo { *; }
-keep class android.net.ProxyProperties { *; }

# BlackOut (extras/blackout): the Xposed entry and the status object are found by name from outside; the Xposed API itself comes from the framework
-keep class com.umutk.pulse.extras.blackout.XposedInit { *; }
-keep class com.umutk.pulse.extras.blackout.Status { *; }
-keep class com.umutk.pulse.extras.blackout.AppWatch { *; }
-dontwarn de.robv.android.xposed.**
