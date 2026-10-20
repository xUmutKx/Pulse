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
