# Watchdog: satırlar ortak dosyalara (ajan 20 uygular)

Bu klasör ortak AndroidManifest.xml ve build.gradle.kts dosyalarına dokunmaz.

## AndroidManifest.xml (app/src/main)

İzinler (mevcut `FOREGROUND_SERVICE` ve `FOREGROUND_SERVICE_SPECIAL_USE` satırlarının yanına):

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

Servisler (`<application>` içinde, mevcut `MonitorService` satırının yakınına):

```xml
<service android:name="com.umutk.pulse.extras.watchdog.MusicPlaybackService" android:exported="false" android:foregroundServiceType="mediaPlayback" />
<service android:name="com.umutk.pulse.extras.watchdog.VideoPlaybackService" android:exported="false" android:foregroundServiceType="mediaPlayback" />
<service android:name="com.umutk.pulse.extras.watchdog.AmbientPlaybackService" android:exported="false" android:foregroundServiceType="mediaPlayback" />
```

Yeni `<receiver>` yok. `CheckWorker` WorkManager ile çalışır.

## build.gradle.kts (app)

`implementation("androidx.work:work-runtime-ktx:2.10.0")` (ajan 19 notu, `19-manifest-needs.md`). `ServiceWatchdog.kt` buna bağlı; yoksa derleme kırılır.

## Notlar

- `MediaService.start(c, MusicPlaybackService::class.java)` çağıran ekran, müzik/video/ortam sürümü için ayrı ayrı çağırır.
- Tile (`QuickTileContract`) ve ikon (`ic_qs_last_played`) ajan 20'nin işi; buradan dokunulmadı.
- Oynatıcı mantığı `onResume(last)` içinde ajan 15 (ortam) ve ajan 17 (müzik) tarafından bağlanır.
