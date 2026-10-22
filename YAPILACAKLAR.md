# Yapılacaklar

Önceki liste (2026-10-17 gece) _arsiv/eski-notlar/YAPILACAKLAR-2026-10-17-oncekiler.md dosyasına taşındı. Eski uzun liste `_arsiv/eski-notlar/YAPILACAKLAR-2026-10-17.md` dosyasında (ayrıntılar orada). Önceki arşiv: `_arsiv/eski-notlar/YAPILACAKLAR-eski.md`.

Lejant: 🔴 açık · 🟡 kısmen yapıldı · 🟢 kodda hazır

---

## 🆕 GECE PAKETİ (2026-10-17): Extra Apps + Ses/Müzik — 20 ajan

Kapsam (kullanıcı isteğinin özeti):
- **Extra Apps mağazası** (Pulse içinde, Microsoft Store tarzı): ayrı sayfa; ek modüller buradan indirilir; uygulama listesinde görünmez; her modülün toplam boyutu yazılır. Fossify Messages'a da aynı mantıkla eklenebilsin.
- **Ortam sesi modülü** (indirilebilir): ateş, rüzgar, dalga, damlama, kafe, sinema, konuşma, çan, havan… en az 20 ses; her biri ayrı açılıp kapanır, toplu seçilir, her sesin seviyesi ayrı; 20 hazır preset + kullanıcı kendi presetini kaydeder; Quick panel'e ikon; son çalınan karışım görünür.
- **Kuş sesleri**: 20+ tür, ayrı ve toplu seçilebilir, her biri ayrı seviyeli.
- **Müzik çalar** (Winamp / Poweramp görünümü): Winamp tarzı skinler (en az 20), Poweramp görünümü, arka plan siyah ya da albüm kapağı; Windows 7 / XP / 95 / Material You temaları; M3U okuma; EQ; widget; arka planda ölmeyen watchdog.
- **Windows Media Player 7 görselleri** (projects klasöründeki 7 görsel + internet) ve Windows için Winamp tarzı masaüstü çalar (ayrı proje).
- **Lyrics**: internetten arama + kaydetme (açık API). YTDLnis kuruluysa ondan indirme entegrasyonu. Kendi YouTube indiricimiz YAZILMAYACAK (ToS).
- **Playlist**: YouTube / Spotify playlist'ini gösterip şarkı çalabilen FOSS istemci araştırması.
- **Video**: Next Player (FOSS) yalnız Extra Apps'ten indirilebilir modül olarak.
- **Büyük dosyalar repoya gömülmeyecek**: indirme linkleri manifest'te; bizim kopya GitHub Release asset'i olarak tutulacak ki upstream link bozulunca bizimki bozulmasın. FOSS modüller için upstream sürüm takibi.
- **Watchdog**: arka planda uygulamalar ölüyor; izleyici eklenecek.
- **Dosyalar (Pulse)**: seçili öğenin kes/kopyala komutları ek menüde çıksın (ayrı açılmasın); yol çubuğu: bulunulan klasör açık gri, diğerleri koyu gri, yazı beyaz; büyütünce görsel kalitesi düşüyor (thumbnail çözünürlüğü).
- Derleme bu pakette YOK (kullanıcı "compile al" demeden Gradle çalışmaz).

### Ajan dağılımı ve dosya sahipliği
Araştırma ajanları (1–14) yalnız `.scratch/extra-apps/<no>-<konu>.md` yazar, kod dokunmaz. Kod ajanları (15–19) yalnız kendi paketini düzenler: `extras/<alan>/`. Ortak dosyalar (AndroidManifest, build.gradle, nav, Dosyalar) yalnız ajan 20'de.

1. Ortam sesi kaynakları ve lisanslar (≥20 ses, CC0/CC-BY, indirme URL, boyut) → `01-ambient-sources.md`
2. Kuş sesi kaynakları (≥20 tür, xeno-canto/freesound, lisans, boyut) → `02-bird-sources.md`
3. Winamp skin kaynakları (≥20, lisans, boyut) → `03-winamp-skins.md`
4. Poweramp görünümü referansı ve tema anatomisi (ekran görüntüleri `/sdcard/Download/projects/Screenshot_*_Poweramp.png`) → `04-poweramp-look.md`
5. Windows Media Player 7/XP/Win7 görselleri: projects'teki 7 görsel + internet, lisans → `05-wmp-visuals.md`
6. FOSS Android müzik çalar taraması (EQ, M3U, Material You, lisans) → `06-foss-players.md`
7. Windows için Winamp benzeri FOSS masaüstü çalar (GitHub + Reddit) → `07-desktop-winamp.md`
8. Lyrics: LRCLIB ve benzeri açık API, .lrc formatı → `08-lyrics-api.md`
9. YouTube / Spotify playlist FOSS istemciler ve yasal sınır → `09-playlist-clients.md`
10. Next Player (FOSS video): lisans, entegrasyon yolu → `10-next-player.md`
11. YTDLnis entegrasyon yolu (intent, lisans) → `11-ytdlnis.md`
12. Extra Apps mağaza ekranı tasarımı (Material You, Store tarzı, toplam boyut, kategori, arama) → `12-extras-store-design.md`
13. Modül manifest şeması, dağıtım (GitHub Release asset, sha256, upstream takip), "listede görünmesin" kuralı → `13-manifest-distribution.md`
14. 20 hazır ortam presetinin tanımı ve kullanıcı preset kayıt modeli → `14-ambient-presets.md`
15. Ortam motoru kodu: çoklu katman, ayrı seviye, toplu aç/kapa, arka plan çalma (`extras/ambient/`); sözleşme `.scratch/extra-apps/15-engine-contract.md`
16. Kuş sesi modülü kodu: tür listesi, toplu seçim, ayrı seviye (`extras/birds/`); 15'in motorunu kullanır
17. Müzik çalar çekirdeği kodu: M3U okuma/yazma, EQ, kuyruk (`extras/music/`)
18. Skin/tema motoru kodu: Winamp .wsz çözümleyici, Poweramp tarzı tema, siyah/albüm kapağı arka plan (`extras/music/skin/`)
19. Watchdog ve arka plan servisi (`extras/watchdog/`) + Quick panel ikonu ve "son çalınan" kutucuğu
20. Ortak entegrasyon: Extra Apps sayfası, manifest, nav, Fossify Messages ekleme, Dosyalar düzeltmeleri (kes/kopyala menüsü, yol çubuğu renkleri, thumbnail kalitesi). Derleme yok.

Durum: 🔴 başladı (ajanlar bitince güncellenecek).

---

## 🆕 Yeni istekler (2026-10-17 gece, 2. tur)

### 2. TUR (kullanıcı: "yarım yamalak olmasın, eksik bırakma, her özelliği tam yap, FOSS varsa indir ve kullan, boyutu küçük tut")
Kararlar: Varsayılan look Material You. Her look (Win95/XP/7/10/11, AMOLED, Material) kendi stilinde. Düz kaydet üstüne yazar; ayrıca "TXT olarak kaydet" seçeneği. Yer tutucu ("Yakında") YOK.
Kullanıcının 7 görseli: /sdcard/Download/projects/ içinde 17 Ekim 22:44–22:45 arası 7 dosya (Poweramp x4, WinAmp_5.9.2, 1-images.jpeg, eC3qke...png).

15 ajan:
1. 7 görsel incelemesi: her görselin öğeleri (düzen, kontroller, renk, tipografi) → .scratch/extra-apps/2tur/01-gorseller.md
2. Ortam sesi paketi: ≥20 ses GERÇEKTEN indirilir, ogg 64 kbps'e çevrilir, sha256 + boyut, manifest → paket/ambient/
3. Kuş sesi paketi: ≥20 tür GERÇEKTEN indirilir (xeno-canto CC), ogg, manifest → paket/birds/
4. Winamp skin paketi: ≥20 skin topla (interglacial + diğer açık kaynaklar), sadece lisansı açık olanlar, boyut → paket/skins/
5. Poweramp arayüzü (kod): extras/music/poweramp/ — kapak, seekbar, kontroller, siyah/albüm arka planı, 4 sekmeli alt bar
6. Winamp klasik arayüzü (kod): extras/music/winamp/ — 275x116 pencere, visualizer, playlist, EQ penceresi, widget
7. Windows Media Player 7 arayüzü (kod): extras/music/wmp7/ — compact/full mod, skin
8. Windows look'ları (kod): extras/music/looks/ — 95/XP/7/10/11 + AMOLED + Material (varsayılan); her look kendi çerçeve, font, renk
9. Müzik çekirdeği tamamlama: Media3 (Apache-2.0) ile oynatıcı, kuyruk, EQ, M3U okuma/yazma, arka plan oynatma; build.gradle değişikliği 20'ye bildirilir
10. Lyrics tam: LRCLIB istemci, önbellek, .lrc kaydetme, senkron gösterim (extras/lyrics/)
11. Video oynatıcı: Next Player intent entegrasyonu, kurulu değilse F-Droid bağlantısı; ayrıca Pulse içi video oynatma (Media3) (extras/video/)
12. Watchdog tam: müzik, video, ortam servisleri için foreground servis + yeniden başlatma + pil muafiyeti isteği (extras/watchdog/)
13. Extra Apps mağaza tam: arama, kategoriler, öne çıkanlar, detay, indir/iptal/güncelle/kaldır, sha256 doğrulama, toplam boyut; look'a göre görünüm (extras/store/)
14. Quick panel: "son çalınan" kutucuğu, ikon, dokununca devam (extras/watchdog/ ve Quick panel ortak dosyası: sadece 14 yazar)
15. Files ve kayıt: "TXT olarak kaydet", düz kaydet üstüne yazma, eski Office formatlarında kaydetme davranışı, kontrast düzeltme (AA), son birleşim kontrolü ve derleme öncesi çakışma taraması

Kurallar (hepsi): Gradle YOK. Derleme YOK. git commit/push YOK. Ayrı dosya sahipliği; ortak dosyalar (Explorer.kt, Tools.kt, Menus.kt, MoreSettings.kt, AndroidManifest, build.gradle) yalnız 13, 14, 15 ve 9'un belirttiği satırlarda. Yorum az. İpucu yazısı yok. Bitmeden durma: her özelliği listele, neyi bitirmediğini açıkça yaz.


- 🟢 Ajan 14 (ortam presetleri) bitti: .scratch/extra-apps/14-ambient-presets.md
- 🟢 Ajan 15 (ortam motoru kodu) bitti: Pulse/.../extras/ambient, derlenmedi
- 🟢 Ajan 5 (WMP görsel envanteri) bitti: .scratch/extra-apps/05-wmp-visuals.md
- 🟢 Ajan 16 (kuş modülü kodu) bitti: Pulse/.../extras/birds, derlenmedi
- 🟢 Ajan 4 (Poweramp görünüm referansı) bitti: .scratch/extra-apps/04-poweramp-look.md
- 🟢 Ajan 11 (YTDLnis) bitti: .scratch/extra-apps/11-ytdlnis.md
- 🟢 Ajan 10 (Next Player) bitti: .scratch/extra-apps/10-next-player.md
- 🟢 Ajan 12 (Extra Apps mağaza tasarımı) bitti: .scratch/extra-apps/12-extras-store-design.md
- 🟢 Ajan 6 (FOSS müzik çalar taraması) bitti: .scratch/extra-apps/06-foss-players.md
- 🟢 Ajan 8 (lyrics API) bitti: .scratch/extra-apps/08-lyrics-api.md
- 🟢 Ajan 9 (playlist istemcileri) bitti: .scratch/extra-apps/09-playlist-clients.md
- 🟢 Ajan 13 (manifest ve dağıtım) bitti: .scratch/extra-apps/13-manifest-distribution.md
- 🟢 Ajan 17 (müzik çekirdeği kodu) bitti: Pulse/.../extras/music, derlenmedi
- 🟢 Ajan 18 (skin motoru kodu) bitti: Pulse/.../extras/music/skin, derlenmedi
- 🟢 Ajan 7 (masaüstü Winamp taraması) bitti: .scratch/extra-apps/07-desktop-winamp.md
- 🟢 Ajan 19 (watchdog ve quick tile sözleşmesi) bitti, derlenmedi
- 🟢 Pulse Files: Recent satırı + yeni gelen dosyalar yazıldı, derlenmedi (ajan 22)
- 🟢 Pulse Files: düz kaydet ve root erişimi kodu yazıldı, derlenmedi (ajan 21)
- 🟢 Ajan 20 (entegrasyon + Dosyalar düzeltmeleri) bitti, derlenmedi
- 🟢 Ajan 3 (Winamp skin kaynakları) bitti: .scratch/extra-apps/03-winamp-skins.md
- 🟢 Ajan 2 (kuş kaynakları) bitti: .scratch/extra-apps/02-bird-sources.md
- 🟢 Kök klasör düzeni yapıldı (manifest: .scratch/duzen-manifest.txt, harita: DUZEN.md)
- 🟢 2. tur madde 1 (7 görsel incelemesi) bitti: .scratch/extra-apps/2tur/01-gorseller.md
- 🟡 2. tur madde 1 eksik kalanlar: renk/tipografi değerleri gözle okundu, piksel ölçümü yok; WMP 7 ve Windows 7/10/11/XP/95 çerçeve örneği görsel setinde yok (madde 6-8 için ayrı referans gerekir); AMOLED ve Material You için görsel yok.
- 🟡 Ajan 10 (Lyrics tam): LRCLIB istemci (User-Agent, sıralı istek, 429/503 yeniden deneme), disk önbelleği, arama/eşleştirme, .lrc ve .txt kaydetme (200 karakter sınırı, çakışmada (2)), senkron satır vurgulama, lyrics ekranı yazıldı, derlenmedi. Dosyalar: Pulse/app/src/main/java/com/umutk/pulse/extras/lyrics/ (LyricsCore.kt, LrclibClient.kt, LyricsScreen.kt). Eksik: ana uygulamaya giriş noktası (menü/navigasyon) bağlanmadı, SAF ile klasör seçimi yok (uygulama klasörüne yazıyor), birim testi yok (test klasörü kapsam dışı), enstrümantal görünümü sade metin.

### Ajan 8: Windows look'ları müzik tokenları (extras/music/looks/) — derlenmedi
- Dosyalar: Pulse/app/src/main/java/com/umutk/pulse/extras/music/looks/LookTokens.kt (sabit sözleşme: MusicLook, musicLook()), LookChrome.kt (MusicWindow, MusicButton).
- Poweramp/Winamp/WMP7 ajanlarına: müzik ekranları Look/PTheme'i doğrudan OKUMASIN. Her ekranda val m = musicLook() al; pencere için MusicWindow(m, title, onClose) ve düğme için MusicButton(m, primary, onClick) kullan; renk/yazı için m.bg, m.panel, m.text, m.sub, m.accent, m.onAccent, m.line, m.radius, m.font.
- Sabit alanlar: bg, panel, text, sub, accent, onAccent, line, radius, frame, glassAlpha, glassEdge, titleH, titleBrush, titleText, filled, buttonFace, buttonEdge, buttonText, font. Yeni alan eklenirse bu dosyada yazılı bildirilir.
- Look başına: 95 gri bevel + lacivert başlık; XP Luna başlık + kırmızı kapatma; 7 Aero cam + yeşil düğme; 10 düz ince çizgi; 11 yuvarlak mica; AMOLED siyah, beyaz vurgu; Material ve diğerleri t.radius/Look.cornerDp ile.
- Extra SkinTokens/skinTokensFor (extras/music/skin/SkinTheme.kt) yerinde duruyor; skin ajanları musicLook()'a geçmeli, font ve başlık alanı yalnız MusicLook'ta var.
- Eksik bırakılan: derleme yok, kapatma dışında başlık küçültme/büyütme düğmeleri yok, Win95 düğmesi basılı hâli (press) çizilmiyor.
- 🟢 Ajan 1 (ortam kaynakları) bitti: .scratch/extra-apps/01-ambient-sources.md
- 🟡 2. tur madde 7 (WMP 7 arayüzü) yazıldı, derlenmedi: Pulse/app/src/main/java/com/umutk/pulse/extras/music/wmp7/ (Wmp7Skin.kt: skin renkleri Default/Night, Wmp7Mode, wmpTime; Wmp7Player.kt: Wmp7Player tam mod = menü çubuğu + sol gezgin + görselleştirici + alt transport + seek, compact mod = başlık + görselleştirici + seek + transport + mod düğmesi). Ses motoruna dokunulmadı (MusicPlayerEngine.kt, PlayerQueue.kt). Eksikler: 7 görselin hiçbiri WMP 7 değil (Poweramp x4, Winamp x3, Office, Win95/XP/11), bu yüzden düzen bilgiden çizildi; ses seviyesi kaydırıcısı yok (MusicPlayerEngine'de setVolume yok); görselleştirici verisi çağıran tarafından verilir (engine audioSessionId'yi dışarı vermiyor, Visualizer bağlanmadı); pozisyon için engine akış yayınlamıyor, çağıran periyodik okumalı; menü tıklamaları sadece onMenu(index) çağırır, açılır menü yok; tek bir test yazılmadı (test klasörü kapsamda değil). Ana uygulamaya giriş noktası bağlanmadı.

## Tur2 madde 4: Winamp skin paketi (2026-10-17)
- Paket: paket/skins/OmniChrome.wsz (MIT), paket/skins/AmpForever.wsz (GPL-3.0). Ikisi de main.bmp ve cbuttons.bmp iceriyor.
- Test (paket degil, lisans belirsiz): test/skins/ altinda 32 skin. Sebep: GitHub'da LICENSE yok veya skin telifi ayri/belirsiz; Apple markali MacOSXAqua ve interglacial zip'leri de burada.
- Manifest: paket/skins/manifest.json (34 kayit, boyut, sha256, main/cbuttons kontrolu).
- Eksik/not: IA (archive.org) skinleri indirilmedi. Lisans koordinatore gore 'sart degil' denildi ama kullanici kurali (lisansi acik olmayani paketleme) uygulandi; karar kullanicidan teyit edilmeli.

## Tur2 madde 14: Quick panel 'son çalınan' kutucuğu (2026-10-17)
- Kutucuk: MonitorService.kt içinde LastPlayedTile (TileService). Metin QuickTileContract.label, alt satır Parça/Karışım, dokununca QuickTileContract.resumeIntent ile MainActivity açılır (startActivityAndCollapse, SDK 34 PendingIntent yolu).
- Manifest: LastPlayedTile servisi (BIND_QUICK_SETTINGS_TILE, ic_qs_last_played ikonu).
- İkon: res/drawable/ic_qs_last_played.xml (nota, çizim, beyaz; Material Symbols değil).
- Yapılmadı: MainActivity ACTION_RESUME'u henüz işlemiyor, yani dokununca Pulse açılıyor ama parça/karışım otomatik devam etmiyor. Bağlama 15/17 (oynatıcı) ve MainActivity sahibinde. QuickTileContract.save çağrısı da o ajanlarda.
- Derlenmedi (Gradle yok). Test yazılmadı: kutucuk ince bir katman, mantık sözleşme dosyasında.

## 12. Watchdog tam (ajan 12, tur 2)
- Eklendi: extras/watchdog/MediaService.kt: ortak taban (mediaPlayback foreground, dakikada bir ServiceHealth.beat, null/ACTION_RESUME ile onResume(QuickTileContract.load)). Somut servisler: MusicPlaybackService, VideoPlaybackService, AmbientPlaybackService. start/stop kullanıcı niyetini ServiceWatchdog'a yazar.
- Eklendi: extras/watchdog/BatteryExemption.kt: isExempt + request (kullanıcının dokunduğu düğmeden çağrılır; onayı sistem penceresi verir).
- Mevcut ServiceWatchdog/ServiceHealth/QuickTileContract değiştirilmedi.
- Manifest satırları: extras/watchdog/MANIFEST_NEEDS.md (FOREGROUND_SERVICE_MEDIA_PLAYBACK, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, 3 servis, WorkManager bağımlılığı). Ortak manifeste dokunulmadı; ajan 13/15 bildirimi bekliyor.
- Yapılmadı: oynatıcı bağlantısı (onResume içinde MusicPlayerEngine / AmbientEngine / video oynatıcı) ajan 15, 17, 11 tarafında. Pil muafiyeti düğmesi UI tarafında (look'a göre) henüz yok; BatteryExemption.request çağrılacak.
- Derlenmedi (Gradle yok). Test yazılmadı: mantık ince, sistem API'lerine bağlı.
- 🟡 2. tur madde 5 (Poweramp arayüzü, kod): extras/music/poweramp/ içinde PowerampStyle.kt (renk parametreleri, materialYou, siyah/bulanık kapak/düz arka plan), PowerampScreens.kt (oynatıcı, kütüphane, arama, efektler/EQ, kuyruk, alt sekmeler), PowerampApp.kt (MusicPlayerEngine ile bağlantı, mini oynatıcı). Derlenmedi.
  Eksikler: mod satırında karıştır/tekrar yok (motorda yok); EQ kaydırıcıları kayıtlı seviyeleri okumuyor (Equalizer API'si okuma vermiyor, 0'dan başlar); EQ preset seçimi yok; kapak için ImageBitmap çağıranın verdiği art lambda'sı (albüm kapağı yükleyici yazılmadı); Poweramp'ın görsel varlıkları kullanılmadı.
- 🟡 Ajan 6 (Winamp klasik arayüz, extras/music/winamp/) yazıldı, derlenmedi: WinampClassic.kt (275x116 ana pencere + seek + ses + EQ/PL düğmeleri + WinampShade widget formu), WinampPlaylistWindow.kt, WinampEqWindow.kt. Eksik: ses seviyesi için MusicPlayerEngine'e setVolume, EQ kaydırıcıları için Equalizer'a bandLevels() gerekli (çekirdek, ajan 9); visualizer seviyeleri henüz gerçek ses verisine bağlı değil; widget home-screen AppWidget olarak manifeste kayıtlı değil (ajan 14/12 ile); playlist'ten parça silme yok.

## 11 - Video (extras/video/) - derlenmedi
- Yeni: extras/video/VideoPlayer.kt (VideoHub, VideoPlayerScreen: Media3 ExoPlayer + PlayerView, PSlider ile arama, ON_STOP'ta duraklat), extras/video/NextPlayer.kt (NextPlayerCard: kuruluysa Aç, değilse F-Droid'de aç, 52 MiB).
- Bağımlılık (ajan 9 build.gradle.kts'e ekler, aynı sürüm): androidx.media3:media3-exoplayer ve media3-ui (ör. 1.5.1). Bu ajan build.gradle'a dokunmadı.
- Manifest (ajan 9/13 ekler): <queries> içine <package android:name="dev.anilbeesetti.nextplayer" /> (yoksa Android 11+ kurulu kontrolü null döner).
- Giriş noktası: Extra Apps (ajan 13) VideoHub(onBack) çağırır; NextPlayerCard() mağaza kartında da kullanılabilir.
- Doğrulanmadı: derleme yok, Media3 API imzaları derlemede kontrol edilecek; Next Player 0.19.0 manifestinde VIEW yok, bu yüzden video dosyası aktarılmıyor.

## 9. Müzik çekirdeği (Media3) — derlenmedi
- Oynatıcı: MusicPlayerEngine artık ExoPlayer (androidx.media3 1.4.1, Apache-2.0). Kuyruk = ExoPlayer playlist; PlayerQueue.kt silindi.
- Ses odağı, kulaklık çıkarma (ses kesme), wake lock: ExoPlayer ayarlarıyla.
- Arka plan: MusicPlaybackService (MediaSessionService) aynı player’ı MediaSession olarak paylaşır; engine MediaController ile servisi bağlar.
- EQ: Equalizer.kt, ExoPlayer audioSessionId değişince yeniden bağlanır.
- M3U: M3uParser.kt değişmedi (BOM kaçışı eklendi); testler app/src/test/.../music/M3uParserTest.kt (MusicCoreTest.kt silindi).
- build.gradle.kts: media3-exoplayer + media3-session 1.4.1, testImplementation junit 4.13.2.
- YAPILMADI / MANIFEST GEREKİYOR (ortak dosya, 9 dokunmadı): AndroidManifest.xml içine
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/> ve
  <service android:name="com.umutk.pulse.extras.music.MusicPlaybackService" android:exported="false" android:foregroundServiceType="mediaPlayback"><intent-filter><action android:name="androidx.media3.session.MediaSessionService"/></intent-filter></service>
  Bu olmadan servis bağlanmaz, arka plan ve bildirim çalışmaz.
- YAPILMADI: çağıran UI (extras/music/poweramp, winamp, wmp7) için engine.state kullanımı; M3U dosyası okuma/yazma IO (sadece saf parser var); tekrar/karıştırma; parça süresi (Media3 kuyrukta vermiyor, -1).
- Derleme ve test çalıştırılmadı (Gradle yasak). Risk: MediaController.releaseFuture ve Player.Listener imzaları derlemede doğrulanmalı; Android 12+ arka planda başlatma kısıtı.

## 15. Files ve kayıt — derlenmedi
- Düz Kaydet: SaveTo.overwrite (ajan 21) aynı yola yazar, kopya üretmez; hata fırlatır, orijinal bozulmaz.
- TXT olarak kaydet: Word (OfFileOps saveTxt, OfDoc.txt = WDoc.plain()) ve metin düzenleyici (OfficeText.kt, TextSnippet ikonu). Üstüne yaz anahtarı OfSaveAsDialog(overwrite = true) ile.
- Eski formatlar (.doc/.rtf/.odt/.xls/.ppt): Kaydet artık OfOldFormatDialog açar: Üstüne yaz (aynı klasörde .docx/.xlsx/.pptx, özgün dosya değişmez), Farklı kaydet, TXT olarak kaydet.
- Chip kontrastı: Poweramp pill (PowerampScreens.kt Pill) artık opak koyu gri #2B2B2B (beyaz yazı 14.2:1), açık temada #EDEDED (siyah yazı 17.9:1). Eski %10 beyaz panel açık arka planda 2.4:1 kalıyordu. Not: dosya ajan 5 sahipliğinde, sadece Pill değişti.
- Ortak dosya taraması: Explorer.kt iki çift import (navigationBarsPadding, WinAlert) silindi; OfficeViewer.kt çift clip importu silindi. Çift üst düzey tanım yok (uzantı fonksiyonları ve overload). Eksik import yok (xpCaption PTheme.kt içinde, internal).
- YAPILMADI / RİSK: Derleme yok, bu yüzden Compose imzaları ve lambda tipleri derlemede doğrulanmalı. TXT yalnız Word ve metin düzenleyicide var; Excel, PPT, PDF için yok. TXT üstüne yazma mevcut .txt dosyasını değiştirir. Eski formatta TXT, ekrandaki kaydedilmemiş değişiklikleri de yazar.

## 13. Extra Apps mağazası (tam) — derlenmedi
- Yazıldı: extras/store/StoreModules.kt (manifest okuma, sürüm/boyut yardımcıları, önbellek + ETag yenileme, FOSS listesi), StoreInstaller.kt (indirme, sha256, veri zip açma, apk imza kontrolü, kaldırma), StoreScreen.kt (arama, kategori, öne çıkanlar, detay, kurulu sekmesi, güncellemeler, toplam), StoreLook.kt (kart / düz / klasik kabuk), test: app/src/test/.../extras/store/StoreModulesTest.kt.
- Eksik / sınır: paket/ambient, birds, skins manifest.json dosyaları henüz yok (ajan 2-4), assets/extras/<paket>/manifest.json olarak eklenmeli. Uzak extras/manifest.json henüz repoda değil. Ekran görüntüsü alanı yok. Mağaza ⋮ menüsü ve üst geri oku yok (Tools.kt ortak, sistem geri tuşu kullanılır). Next Player yalnız F-Droid bağlantısı (ajan 10), YTDLnis yalnız kuruluyken görünür ve bağlantı gönderir (ajan 11).
- Gradle çalıştırılmadı, derleme yapılmadı.

## Pulse varsayilan logo: siyah zemin, beyaz P (derlenmedi)
- ic_launcher (adaptive): arka plan #000000 (values/colors.xml launcher_bg), on plan beyaz kalin P (drawable/ic_launcher_fg.xml, evenOdd).
- Eski launcher PNG: mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi ic_launcher.png (48-192 px), PIL ile uretildi.
- Look ikonlari (ic_ti_*, ic_files, ic_calendar, ic_contacts, ic_photos) dokunulmadi.
- minSdk 31 oldugu icin PNG lar cihazda kullanilmaz; yalniz istek geregi uretildi.
- 🟢 Ajan 3 (kuş sesi paketi) bitti: 22 kayıt ogg (opus, 64 kbps, mono) paket/birds/, manifest.json sha256+boyut+süre ile. Lisans: CC BY / CC BY-SA (ses lisansı şartı yok kararı sonrası). Eksik: Kuzgun, yeşil ördek, kartal bulunamadı. Gri orman tavuğu tür adı doğrulanmadı. Kayıtçı adları çıkarılmadı.

- [x] Ajan 2 (ortam sesi paketi) bitti: 23 ses ogg 64 kbps, toplam 19.164.630 bayt (~18,3 MB), sha256+boyut manifest.json icinde (paket/ambient/). Kaynak: Internet Archive (SSE Library CC0, fire-sound-effects CC0, TenMinutes noise, SoundBible). Commons 429 limitine takildi. Eksik preset kimligi: havan (ses bulunamadi). Gurultu dosyalari 10 dk, kirpilmadi.

## Shizuku düğmesi — derlenmedi
- Kök neden: Shell.askShizuku() yalnız Shizuku çalışıyor ve izin verilmemişse (durum 1) requestPermission çağırıyordu. Shizuku kapalıyken (durum 0) veya kurulu değilken hiçbir şey yapmıyor, hata sessizce yutuluyordu. Manifest (queries, ShizukuProvider), addBinderReceivedListenerSticky ve addRequestPermissionResultListener doğruydu.
- Sampler.kt: askShizuku(ctx) artık durum bazlı: kurulu değilse indirme sayfasını açar; kurulu ama başlatılmamışsa 'Shizuku başlatılmamış' gösterir; çalışıyorsa requestPermission(1) çağırır. Sonuç (verildi/reddedildi) shizukuMsg ile dinleyiciden gelir. shizukuMsg yeni state.
- AccessCard.kt, Pages.kt (RootSettings), Installer.kt (Shizuku satırı): çağrılar askShizuku(ctx) olarak güncellendi; mesaj gösterilir; kurulu-başlatılmamış durumu 'Shizuku başlatılmamış' oldu.
- Yalnız Shizuku ile ilgili kod değişti. Gradle/derleme/git çalıştırılmadı.
- Cihazda test: (1) Shizuku yüklü değilken düğme -> indirme sayfası açılmalı; (2) yüklü ama kapalıyken -> 'Shizuku başlatılmamış' yazmalı, hiçbir pencere açılmamalı; (3) Shizuku çalışırken -> izin penceresi çıkmalı, verince/reddedince sonuç satırı değişmeli; (4) Installer'daki Shizuku satırına basınca aynı davranış.

## Ajan 20 (tur 2 bağlantı) - derlenmedi
- Manifest: 3 watchdog servisi (MusicPlaybackService, VideoPlaybackService, AmbientPlaybackService; exported=false, mediaPlayback) + extras.music.MusicPlaybackService (MediaSessionService intent-filter) eklendi. Aynı basit ad iki sınıfta var; bkz. aşağıdaki "Kalan" maddesi.
- Manifest izinler: FOREGROUND_SERVICE_MEDIA_PLAYBACK, FOREGROUND_SERVICE_DATA_SYNC, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (POST_NOTIFICATIONS zaten vardı). <queries>: dev.anilbeesetti.nextplayer, com.deniscerri.ytdl eklendi (moe.shizuku zaten vardı).
- Manifest MainActivity: com.umutk.pulse.action.RESUME_LAST intent-filter eklendi.
- build.gradle.kts: work-runtime-ktx 2.10.0 eklendi. media3 1.4.1 (exoplayer, session) zaten vardı, çakışma yok.
- QuickTileContract.LastPlayed: ref alanı (dosya yolu) eklendi, varsayılan "" (geriye uyumlu). MusicPlayerEngine her parça geçişinde save çağırır.
- MainActivity.handleShare: ACTION_RESUME -> resumeLastPlayed (kuyruk varsa play, yoksa son parçayı ref ile playQueue).
- Menü: Ek uygulamalar menüsüne Video (VideoHub), Şarkı sözleri (LyricsHub), Kuş sesleri (BirdsHub) eklendi; Tools.kt SubScreen id "video", "lyrics", "birds".
- Yeni dosyalar: extras/birds/BirdsHub.kt, extras/lyrics/LyricsHub.kt. BirdsScreen hâlâ Material Switch/Slider kullanıyor; look kuralına göre yeniden yazılmalı.
- KALAN (bağlanmadı): (a) Müzik ekranı yok: Poweramp/Winamp/WMP7 hiçbir yerden çağrılmıyor, kütüphane kaynağı yok, musicLook seçimi yapılmadı; menüye giriş eklenmedi. (b) Ambient için UI yok; AmbientPlaybackService hiçbir ekrana bağlı değil. (c) Watchdog MusicPlaybackService ile extras.music.MusicPlaybackService aynı işi yapıyor; ikisi de manifestte, iki bildirim çıkabilir, tek sınıfa indirilmeli. (d) Watchdog servislerinin onResume içi boş; MediaService.start çağıran yok. (e) BatteryExemption.request çağrısı UI tarafında yok. (f) Mağaza tarafına Next Player/YTDLnis dışında ek giriş yapılmadı.

## İndirme yöneticisi: hız, bildirim, arka plan (derlenmedi)

Ölçüm: kod okuma ile (cihazda ölçülmedi). Durum: parçalı indirme (6 bağlantı, 64 KB tampon) ve foreground servis zaten vardı; bildirim ve arka plan sorunu büyük ölçüde mağaza indirmesinden geliyordu (tek bağlantı, tek akış, servis yok, bildirim yok).

Değişenler:
- features/Downloads.kt: PARTS 6 -> 4 (kullanıcı isteği: 2-4). Tampon 64 KB -> 256 KB. Bağlantı zaman aşımı 20 s -> 15 s, okuma 30 s aynı.
- Bayt sayacı: her parçada StateFlow güncellemesi yerine DlTask.count(): en fazla 500 ms'de bir yayınlanır, transfer bitince flush().
- DownloadService döngüsü: hız ve ilerleme 500 ms; bildirim ve kayıtlı liste 1 s (sistem bildirim güncellemesini sınırlar).
- Servis tipi specialUse -> dataSync (Downloads.kt ve AndroidManifest.xml: DownloadService satırı). FOREGROUND_SERVICE_DATA_SYNC izni zaten vardı. targetSdk 34 olduğu için 6 saatlik zaman aşımı henüz geçerli değil; targetSdk 35 olursa onTimeout eklenmeli.
- Yeni API: Downloads.fetchFile(c, url, file, size, onProgress), askıya alınabilir, bitene kadar döner, IOException veya CancellationException fırlatır. Bildirim, servis ve parçalı/tek bağlantı mantığı aynı.
- extras/store/StoreInstaller.kt: fetchTo artık Downloads.fetchFile kullanıyor (eski tek bağlantılı akış kaldırıldı). Yalnız https kabul edilir, listedeki boyuttan büyük dosya hâlâ reddedilir, sha256 kontrolü aynen.
- Mağaza indirmeleri (quiet): indirme listesinde görünmez, bitince bildirim yok, kayıt dosyasına yazılmaz, duraklatma ve takılma yeniden başlatması uygulanmaz; iptal bildirimden çalışır.
- Bildirim: çalışan indirme ad, yüzde, hız, kalan süre ve Duraklat/İptal gösterir; biten için 'İndirildi' (Aç), hata için 'İndirme başarısız' (Yeniden dene). Kanal Downloads ve Finished downloads olarak zaten vardı.
- POST_NOTIFICATIONS: İndirme sayfası açılınca istenir (zaten vardı). Reddedilirse indirme sürer, yalnız bildirimler görünmez.

Hız ölçümü (cihazda, aynı dosya, aynı ağ):
1. Bir büyük dosya (500 MB+, Range destekleyen sunucu) için Downloads sayfasındaki hızı not et. Aynı linki 2 kez daha indirip ortalamasını al.
2. Yeni APK'yı kur, aynı linki indir, 30 saniyelik ortalama hızı karşılaştır (önce ve sonra).
3. Bildirimdeki yüzde ve hız, sayfadakiyle aynı mı, 1 s'de bir mi güncelleniyor, saymadan kontrol et.
4. Uygulamayı arka plana al, ekranı kapat 3 dakika bekle, bildirimdeki bayt artıyor mu, dosya boyutu büyüdü mü.
5. Uygulamayı kaydırıp kapat (son uygulamalardan), bildirim ve indirme sürüyor mu.
6. Mağazadan bir modül indir: satır ilerliyor mu, bildirim çıkıyor mu, bittikten sonra kurulum penceresi açılıyor mu.

Açık kalanlar:
- Cihazda ölçüm yapılmadı; 4 parça yeterli mi, sunucu bağlantı başına sınır koyuyor mu, bilinmiyor.
- Derleme ve Kotlin kontrolü yapılmadı (compile_check.sh Pulse).
- Manifest başka bir ajan tarafından da değişiyor olabilir; derlemeden önce XML'i doğrula.
- Mağaza indirmesinin bildirimi geçici dosya adını (store-<id>.apk) gösteriyor; modül adı verilmedi.
- Mağaza indirmeleri duraklatılamaz ve yeniden denenmez (hata satırında görünür).
- [x] Ajan 2 ek: ambient.zip (dagitim asset, 19.179.475 bayt, sha256 7662d3fc94c779125990ca7483313ebebe1281d1254faca6dfd7acd8f33899dc) paket/ dizininde. APK assets kopyasi YAPILMADI: 13-manifest-distribution.md buyuk ses paketlerinin repoya/APKye gomulmemesini, GitHub Release asset olarak dagitilmasini soyluyor.

## BlackOut: beyaz açılan siteler (kısmen, derlenmedi)
- Kök neden (kod okuma, cihazda üretilemedi): WebView koyulaştırması (algorithmic darkening / force dark) bazı sitelerde sayfayı beyaza düşürüyor. Hariç tutma zaten vardı (MethodsUi "Sites left as they are", web_skip, WebBlack.tick); eksik olan: URL yazılınca eşleşmemesi.
- Değişen: ModuleHooks.kt WebBlack.parseSkip artık tam URL, şema, yol, port ve başta www./nokta kabul eder ("https://www.example.com/a" -> example.com).
- Açık kalanlar: fd_apps (uygulama geneli force dark) açıkken HWUI koyulaştırması hariç tutulan siteye de uygulanabilir; View.setForceDarkAllowed hook'u her zaman true yapıyor. Cihazda test edilmedi, derleme yok.

## Pulse: tablet ve yatay düzen (kısmen, derlenmedi)
- ui/Responsive.kt zaten vardı (WinClass, MaxWidthBox, ListDetail); değiştirilmedi.
- Uygulamalar (MorePages.kt AppsPage): tablette (Expanded) liste solda 380 dp, detay sağda; telefonda eskisi gibi alt ekran. Sayfa artık CappedPages içinde değil, dar ekranda MaxWidthBox ile 720 dp.
- Kabuk: Material rail, Win11 yan menü, Win10/klasik kabuklar zaten vardı; değiştirilmedi.
- Yatay çentik: Pulse kökü ve bağımsız Dosyalar kökü Surface.displayCutoutPadding() aldı (kabuklar üst/alt çubukları zaten pad ediyordu).
- Dönüş: MainActivity configChanges listesine smallestScreenSize ve screenLayout eklendi (katlanabilir/çoklu pencere boyutu değişiminde activity yeniden kurulmaz; orientation zaten vardı, durum rememberSaveable ile korunuyor).
- Açık kalanlar: Dosyalar için liste+detay yok; Müzik ayrı bir Pulse sayfası değil, dokunulmadı; Mağaza zaten 720 dp sınırlı; Gradle derlemesi ve cihazda üç look (Win95, Material, AMOLED) kontrolü yapılmadı.
- [Pulse, derlenmedi] Müzik sayfası MusicHub doğrulandı (imzalar eşleşti, değişiklik yok). Tools.kt: "lyrics" ve "birds" rotaları kaldırıldı, "whitenoise" -> WhiteNoiseHub(back) eklendi.
- Store: BUNDLED_PACKS'ten birds çıktı, yerleşik satıra Beyaz gürültü eklendi. Menus.kt Ek uygulamalar hâlâ Dosyalar/Fotoğraflar/Takvim/Kişiler/ArtCraft içeriyor (sadece Mağaza değil; karar bekliyor).
- WhiteNoiseHub ve extras/birds silinmesi başka ajanda; derleme yapılmadı.
- Pulse Contacts: detay ekranında çöp kutusu tek dokunuşla siliyor (onay penceresi kaldırıldı), kutu sola yaslı menteşeden -25° açılıyor (250 ms), üst çubuk 40 dp hedeflerle 360 dp telefonda sığıyor. derlenmedi
- Pulse Contacts: BinIcon çağrısı CtBinButton helper'ı içinde, tüm görünümlerde aynı yönde eğilir. derlenmedi
- Pulse Contacts: ACTION_INSERT girişi zaten doğrudan düzenleyiciyi açıyor (ctInInsert -> CtScreen.Edit); kod değişmedi. derlenmedi

- Extra apps store: Processes sayfası temayı Material yapıyordu; MainActivity.kt Root, sayfa/araç değişiminde Cfg.load+Opt.load ile Pulse'un kendi görünümünü geri yüklüyor (derlenmedi).
- Kök neden: Look process-genel, Photos/Calendar/Contacts/Files apply() yazıyor; Pulse sadece onStart/onResume'da geri yüklüyordu.
- Geri tuşu: mağaza kendi okunu çiziyor (StoreScreen.kt:150), shell ikinci ok eklemiyor; geri yolu onSub("") ile aynı restore'dan geçiyor (derlenmedi).
- Pulse: kuş sesleri sayfası kaldırıldı, yerine beyaz gürültü karıştırıcısı geldi (WhiteNoiseHub: beyaz gürültü, kahve, çan, yağmur, ateş, rüzgar, dalga). derlenmedi
- 7 döngü WAV (22050 Hz mono 16-bit, 12 sn, toplam ~3.6 MB) tools/gen_whitenoise.py ile üretildi; ilk açılışta filesDir/extras/whitenoise altına kopyalanır.
- Kalan: StoreScreen.kt'de 'kus' ('Birds') etiketi hâlâ duruyor; derleme yapılmadı. derlenmedi

## Extra apps büyük tur (2026-10-18, derlenmedi) — 16 ajana bölündü
Kaynak: kullanıcının yapıştırdığı uzun istek. Ortak kurallar: .scratch/extra-apps/AGENT-RULES.md. Yayın (push/release) yok, sadece yerelde hazırlık; derleme "derle" denince.
1. ambient-pack: 20+ ortam sesi paketi (yağmur, ateş, rüzgar, dalga, damla, kafe, sinema, konuşma, çan, singing bowl...) -> paket/ambient + manifest parçası
2. bird-pack: ayrı kuş sesleri paketi (çeşitli kuşlar) -> paket/birds
3. ambient-ui: karıştırıcı sayfası, her sesin ses seviyesi, 20 hazır preset + kullanıcı presetleri, kuşlar tek tek/toplu seçim, AmbientLast (son çalınan)
4. watchdog-tile: hızlı panel kutucuğu (son çalınan), watchdog bağlama, arka planda ölmeme
5. store-logic: modül indirme/güncelleme, repo aynası (FOSS APK, otomatik güncelleme iş akışı), kurulu extra uygulamalar için güncelleme
6. store-ui: mağaza sayfası (Microsoft Store tarzı Windows look, Material), toplam boyut, güncelleme rozeti
7. music-core: kütüphane, kuyruk, M3U/M3U8, ekolayzer, albüm kapağı arka planı, görünüm seçici
8. poweramp-look, 9. winamp-look (+ skin paketi), 10. wmp-look, 11. material-look (varsayılan oynatıcı)
12. widgets-music: Winamp skin'li ana ekran widget'ları
13. lyrics (ara/kaydet), 14. online-playlists (YouTube/Spotify listeleri, FOSS istemci; YTDLnis varsa intent)
15. video-player (yerleşik, Next Player özellikleri), 16. files-fixes (büyütünce bulanıklık, seçince kes/kopyala doğrudan, yol rengi)
Çelişki notu: yapıştırılan eski istek Next Player'ı indirilebilir extra olarak istiyor; bu oturumdaki son talimat onu mağazadan kaldırıp uygulamaya gömmek. Son talimat uygulanıyor.
- widgets/music: Winamp skin home-screen widget (WinampWidget, WinampWidgetConfig, res/layout/widget_winamp.xml, res/xml/widget_winamp_info.xml); manifest in needs-widgets-music.md; derlenmedi
- [watchdog-tile] LastPlayedToggleTile (ambient toggle, music fallback), ic_qs_last_played redrawn, MediaService wake lock + guarded foreground start + onResume for music/ambient, BatteryExemptionButton composable; manifest/call-site notes in .scratch/extra-apps/needs-watchdog-tile.md (derlenmedi)

[online-playlists] extras/playlists/PlaylistClient.kt + OnlinePlaylistsHub.kt: YouTube via Piped API, Spotify via embed page (track names only, matched to YouTube search), M3U save, YTDLnis intent. Needs: queries line + route + store row in needs-online-playlists.md. derlenmedi
- [wmp-look] Wmp7Skin: 7 on ayar (Default, Night, WMP9, WMP10, WMP11 Aero, Groove, 6.4 Classic), Wmp7Skin.All; kodla cizim, derlenmedi
- [wmp-look] Wmp7Player: dar ekranda (<480dp) agac acilir serit; "#" ile baslayan nav etiketi katlanir grup basligi; derlenmedi

## MaterialPlayer (agent material-look, 2026-10-18) derlenmedi
- extras/music/looks/MaterialPlayer.kt: MaterialPlayer(engine, library, modifier, artBackground=false): tabs Songs/Albums/Artists/Playlists/Folders, search, mini player, now-playing sheet (cover k-means colours, seek, shuffle/repeat, queue reorder, sleep timer, blurred-art background option). Needs trackArt() from music-core (TrackArt.kt).
- Album and artist from Track tags (folder name fallback). Playlists = saved queues in prefs pulse_mp_playlists. No Windows variant yet.
- [lyrics] LyricsStore.kt (yerel kayıt + .lrc yanına yazma), LyricsPanel.kt (LyricsPanel/LyricsBody), LyricsHub yeniden: kayıtlı liste/ara/çalan. LRCLIB + lyrics.ovh. derlenmedi
- [store-logic] StoreInstaller: isStoreInstalled registry, updates(), updateAll, totalBytes/installedBytes helpers; StoreCatalog reads assets/extras/manifest.json fallback (derlenmedi)
- tools/mirror_foss.py + .github/workflows/mirror-foss.yml (yazildi, push edilmedi); extras/manifest.json taslagi: fossify-messages; APK paket/apks/ (derlenmedi)
- needs-store-logic.md: AppManager filtre, asset kopyasi, paket manifest birlestirme (derlenmedi)
- [music-core] Muzik cekirdegi: Library.kt (MediaStore + onbellek + izin), TrackArt.kt, Playlists.kt (m3u8 kaydet/yukle/ice-dis aktar, son kuyruk devam), Equalizer bass/virtualizer/kayit, MusicHub 4 gorunum (Material/Poweramp/Winamp/WMP) + LyricsPanel + kapak arka plani. derlenmedi
- needs-music-core.md: READ_MEDIA_AUDIO izni manifeste eklenmeli. Gorsellestirici (levels) bagli degil. derlenmedi

## ambient-ui (2026-10-18, derlenmedi)
- White noise sayfası karıştırıcıya çevrildi: AmbientMixer (ortak motor tutucu), AmbientLast, WhiteNoisePresets, WhiteNoiseHub yeniden yazıldı.
- 20 hazır ön ayar (sakin olanlar önce, varsayılan "Calm"), kullanıcı ön ayarları SharedPreferences JSON.
- Paketler extras/ambient ve extras/birds pack.json okunur; eksik pakette indir satırı.
- Eksik: AmbientPlaybackService.onResume bağlantısı (needs-ambient-ui.md), paket kimlikleri henüz paketlerde doğrulanmadı.
- (store-ui, derlenmedi) Mağaza sayfası look başına çizildi: 95/XP/7 pencere başlık çubuğu + durum çubuğu, 10/11 Microsoft Store karoları/pivot sekme, Material ve diğerleri Play Store tarzı; arama, kategoriler, öne çıkanlar, Kurulu sekmesi, güncelleme sayacı, % ilerleme, toplam/kurulu boyut satırı, ayrıntı sayfası (geri başlıklı). Dosyalar: extras/store/StoreScreen.kt, StoreLook.kt.
- (store-ui, derlenmedi) "kus" kategori etiketi kaldırıldı (kuş paketleri Ses altında); YTDLnis/Next Player kalıntısı yoktu.

[video-player] Video: ExoPlayer oynatıcı yeniden yazıldı (extras/video: VideoPlayer.kt, VideoLibrary.kt): MediaStore klasör+küçük resim listesi, URL/dosya aç, jest (kaydır-ara, parlaklık/ses, çift dokun, çimdik), hız, görünüm modları, ses/altyazı seçimi + .srt/.ass, PiP, kaldığı yerden devam, döngü, kilit, yönlendirme, arka plan sesi. needs-video-player.md manifest satırları bekliyor. derlenmedi
- Poweramp gorunumu yeniden yazildi (poweramp-look): oynatici, dalga seek, kutuphane kategorileri/klasor agaci, EQ dikey bantlar + preset + bas/tiz, tempo/ses knob, kuyruk surukle-tutacak, 29 skin + onizleme seridi (PowerampPlayer, needs-poweramp-look.md). derlenmedi

[bird-pack] paket/birds/birds.zip hazir: 33 ses (32 tur + safak korosu), 8.9 MB, Vorbis 32kHz mono 40 sn dongu; derlenmedi, yayinlanmadi.
[bird-pack] manifest-birds.json (tek modul nesnesi, sha256 dolu), SOURCES.md, tools/build_bird_pack.py. Lisans CC BY-SA (Wikimedia/xeno-canto); derlenmedi.

## winamp-look (2026-10-18) - derlenmedi
- Winamp: kodla cizilen varsayilan klasik skin (skin/DefaultSkin.kt), tum sprite sayfalari kodda boyaniyor; skin/SkinDraw.kt (blit, text.bmp, dokunma bolgeleri)
- WszParser: BMP 1/4/8/RLE/16/24/32, tum sprite dosyalari, nums_ex, region ilk poligon; loadWinampSkin(File) ve installedSkins(ctx) hazir
- WinampMain: ana pencere + shade; WinampEqWindow/WinampPlaylistWindow eqmain/pledit spritelarindan; WinampScreen: telefon genisligine olcekli, skin secici
- paket/skins/skins.zip: 21 skin + pack.json, manifest-skins.json hazir (yayinlanmadi); tools/fetch_skins.py, build_skin_pack.py
- needs-winamp-look.md: MusicHub'a WinampScreen baglanmali, kurulum klasoru uyusmazligi (external vs filesDir)
- [ambient-pack] 33 sesli indirilebilir paket hazir: paket/ambient/ambient.zip (11.7 MB), tools/build_ambient_pack.py, manifest-ambient.json (url extras-ambient-1, yayinlanmadi). derlenmedi
- Gercek kayit 27, sentez 6 (bowl_rub, fan, konusma, pembe_gurultu, brown, white). Kaynaklar paket/ambient/SOURCES.md; CC BY-SA/BY kayitlar atif ister.

[audio-levels] MusicLevels.kt (FFT tap via ExoPlayer AudioProcessor, rememberLevels(n)), MusicPlayerEngine renderers factory, Poweramp radial+bars behind art/transport, Winamp real spectrum - derlenmedi
- sound-icons: extras/whitenoise/SoundIcons.kt (SoundIcon, ~40 path glyphs, 3 look styles) used in SoundRow; birds via cat param; preset chips unchanged; derlenmedi

## accent-fix (derlenmedi)
- Beyaz vurgu eklendi: PTheme.kt WinAccents/MaterialAccents sonuna 0xFFFFFFFF (indeks 11 / 20); Look.accent win11 beyaz secilince yari saydam yapmaz; win11 AMOLED koyu ikizi varsayilan vurgu beyaz.
- Turuncu kok neden: Files vurgu secici MaterialAccents indeksi yaziyordu, Look.accent win11/win10 icin WinAccents okuyor (Material 4 yesil -> Win 4 turuncu). ExplorerArchive.kt secici accentsFor(T.material) oldu.
- widgets-more (derlenmedi): MusicWidget (2x1/4x1/4x2 size map, art, progress, prev/play/next), VideoWidget (VideoLast reader), NoiseWidget (4 presets) in widgets/music/; Winamp widget: tap-to-seek (16 segments), shuffle/repeat, numbers/text/shufrep/posbar sprites. Manifest + VideoPrefs.setResume snippet: .scratch/extra-apps/needs-widgets-more.md

## Yeni istek (2026-10-18, Pulse, sesle yazılmış) - ajanlara bölündü
1. [install-fix] "Remove and restore data": yedek `cp: bad '/data/data/com.termux/.': No such file` ile düşüyor (root var ama görmüyor, mount namespace?). Shizuku izin istemiyor.
2. [winamp-skins] Winamp skin sayısı en az 50-60 (5-6 siteden), indirme yok uygulamaya gömülü; skinler widget olarak da; video oynatıcı skinleri.
3. [ambient-fix] Yağmur sesi sağanak gibi rahatsız ediyor -> sakin damla damla; kuş sesleri uygulamaya gömülü (ayrı indirme yok).
4. [video-player] Next Player'ın TÜM özellikleri (çok eksik).
5. [poweramp-fix] Poweramp eksikleri: altta ses seviyesi çubuğu, altta sadece oynat tuşu, vurgu beyaz/gri (turuncu değil), albüm kapağı kare + arkası boş, ses/çalma listeleri/istatistikler.
6. [fossify-embed] Fossify Messages (SMS) gömülü, ekstra indirme yok.
Hepsi derlenmedi; derleme "derle" denince.
- [derlenmedi] fossify-embed: Fossify Messages 1.10.1 (6.7 MB) assets/extras/fossify-messages.apk icine konuldu; StoreInstaller.isBundled/bundledCopy ile indirmeden kurulur, satirda "Kur"/"Dahili". Eski surum ise indirme yoluna duser.
- [install-fix] InstallWindow.removeAndRestore: root-only (Shell.runRoot, never Shizuku), probes /data/user/0, /data/data, /data_mirror x 3 mount modes, verifies copy by file count before pm uninstall, keeps backup on failure. derlenmedi
- [install-fix] Shell.askShizuku: isPreV11 + denied-before check opens Shizuku app; manifest needs API_V23 permission (see .scratch/extra-apps/needs-install-fix.md). derlenmedi

## poweramp-fix (derlenmedi)
- Poweramp: varsayilan vurgu notr acik gri (NeutralAccent); turuncu kok neden: skin build() sistem vurgusunu (Pulse turuncu) aliyordu; sistem vurgusu artik sadece "Accent" skininde. Turuncu "Classic orange" skininde duruyor.
- Oynatici: kare tam kapak (Fit, arkada gorsellestirici/blur yok), alt ses cubugu (VolumeBar), alt tusta yalniz oynat; onceki/sonraki baslik satirinda; favori yildizi, uyku zamanlayici (15/30/60).
- Yeni: extras/music/stats/MusicStats.kt (calma sayisi, sure, son calinan, favoriler) + MusicPlayerEngine init icine StatsListener satiri; Kutuphane: Calma listeleri (olustur/yeniden adlandir/sil), Favoriler, Istatistikler, siralama.
- Yok: lyrics, crossfade, replay gain, liste ici siralama, bildirim kapagi.

## ambient-fix (derlenmedi)
- Yagmur: yeni yumusak "rain" (damlama+hafif drizzle, alcak geciren, -27 dB); sagnak "heavy_rain" ayri, Stormy night on ayarinda; clip eden damlama/dere/selale kisildi (tools/build_rain_fix_pack.py).
- Kuslar (33) + ambient paketi APK icine assets/birds, assets/ambient; WhiteNoiseHub indirme satirlari kaldirildi; AmbientMixer.scan assets->filesDir/extras/bundled kopyalar.
- Magaza satirlari kaldirilmali: needs-ambient-fix.md. APK ~+20 MB.

## Video oynatici (video-player ajani, derlenmedi)
- extras/video yeniden yazildi: VideoLibrary (VideoPrefs ~60 secenek, son oynatilanlar, URL gecmisi), VideoBrowse (klasor/dosya/agac, siralama, liste/izgara, arama, NEW + ilerleme cubugu, bilgi/paylas/yeniden adlandir/sil/gizle), VideoPlayer, VideoSubs, VideoSettings, VideoSkins.
- 14 kilif: Next, Material You, WMP 11, VLC, MX, Winamp, Poweramp, Minimal, AMOLED, Win95/XP/7/10/11; her biri kendi arama cubugu + dugme cizimi.
- Oynatici: kaydirma/cift dokunma/uzun basma 2x/sikistirma, hiz, en-boy, yon, kilit, PiP, arka plan, ses artirma, sessizligi atla, dil tercihi, harici altyazi (kodlama, gecikme, boyut), dongu/karistir, klavye.
- Ayarlar: katlanir bolumler. Eksik: SMB/FTP/WebDAV, FFmpeg cozucu, gomulu altyazi gecikmesi. Notlar: .scratch/extra-apps/needs-video-player2.md

[integrator 2026-10-18] manifest: Shizuku API_V23 izni eklendi; assets/extras/manifest.json ambient+birds modulleri cikarildi (APK icinde). Kalan: winamp-skins ajani, skins modulu. derlenmedi
- [winamp-skins] 78 Winamp skins bundled in app/src/main/assets/skins (+index.json, 3.7 MB); picker in WinampScreen shows main.bmp thumbnails with search; no download row. Sources: skins.webamp.org museum, archive.org winampskins, GitHub webamp-demo + macamp. derlenmedi
- [winamp-skins] Widget: any skin per widget id (config activity with live render previews + search), 4x2 and new 4x1 (WinampWidgetCompact, manifest lines in needs-winamp-skins.md); render now draws titlebar, play lamp, stereo, volume. derlenmedi
- [winamp-skins] Tools: tools/fetch_skins.py (download), tools/build_skin_pack.py (validate, strip, dedupe, write assets). Raw downloads in _downloads/winamp-skins/raw. derlenmedi
[integrator] Winamp 4x1 receiver manifeste eklendi, skins modulu manifest.json den cikarildi. Hepsi derlenmedi; sirada: derle onayi, Hakkinda atif metni (kus CC BY-SA)

## Yeni istek 2 (2026-10-18 gece, Pulse, sesle yazılmış) - ajanlara bölündü
1. [noise-ui] Beyaz gürültü hub: her satırda iki ses kontrolü/yuvarlak var, ses çubuğu garip (3/5 yuvarlak) -> sade tek satır, tek ses çubuğu; Winamp gibi widget şekli de olsun (müzik çalar widget'ı da).
2. [music-deep] Poweramp çok eksik (detaylar), Winamp skin seçici sütun değil satır/ayar; online çalma listeleri müzik çalar özelliği; müzik çalar widget'ları.
3. [video-deep] Video oynatıcı hâlâ çok eksik.
4. [store-fossify] Fossify Messages gömülü (MMS dahil), ekstra indirme ayarlarına taşınsın.
5. [theme-gallery] Tüm extra uygulamalarda ek ayarlar + tema önizleme galerisi: satır başına 3 tema, ilk satırın sonunda solan okla kapalı/açılır; her uygulamada en az 50 tema/widget (Winamp, Poweramp, video, beyaz gürültü, müzik).
6. [install-fix2] Root uygulama data klasörünü görmüyor, Shizuku izin istemiyor -> düzelt.
Derleme "derle" denince.
- [theme-gallery] extras/ui/ThemeGrid.kt: ThemeGrid (3/row, fade+chevron), SettingsSection, SwitchRow, SliderRow; notes in .scratch/extra-apps/theme-gallery-api.md. derlenmedi
7. [music-deep] Poweramp hala çok eksik; lyrics oynatıcıda görünür bir tuşla; albüm kapağı ekran genişliğinde tam kare; Winamp'ta da lyrics göstermenin bir yolu; müzik çalara beyaz gürültü (ambient) karıştırıcı eklensin.
8. [app-names] Extra uygulama adları "Pulse: ..." olmasın, sade ad (Music Player, Video Player, White Noise...): launcher/widget/kısayol etiketleri.

- [store-fossify] Fossify Messages: APK in assets has SMS_DELIVER, WAP_PUSH_DELIVER, RECEIVE_MMS (MMS-capable); bundled install path needs no download, older installed version upgrades via system installer (same signature checked). Extras settings page (extras/store/StoreSettings.kt): download source (GitHub/jsDelivr), auto-update, install location (moves data), clear cache, per-extra options (auto-update, keep file, license, source, remove data), also on each detail page. Launcher/widget labels no longer "Pulse X" (Music Player, Video Player, White Noise, Playlists, Text, Office, Winamp). derlenmedi
- [install-fix2] Shell.rootSee + 6 root mount views; removeAndRestore and Files (RootFs.list) browse /data/data via root; findings in .scratch/extra-apps/needs-install-fix.md. derlenmedi

[video-deep] Video: 54 skin (14 eski + 40 paletli, VideoPalettes.kt, ThemeGrid ile galeri); A-B tekrar, uyku zamanlayici, ekolayzir (EqDialog), seek onizleme (MediaMetadataRetriever), HW/HW+/SW cozucu etiketleri, ekran acik tut, ACTION_VIEW + .m3u/.pls ag listesi; Video widget 50 gorunum (VideoWidgetConfig, widget_video_bg_*). Manifest: .scratch/extra-apps/needs-video-deep.md. Yok: bolumler, SMB/FTP/WebDAV, cevrimici altyazi. derlenmedi
9. [lyrics] "Lyrics for Poweramp" benzeri + LrcLrc (xUmutKx/LrcLrc) Pulse'a: sözle şarkı arama (geçen satır + alt/üst birer satır), arama yüzen (floating) pencere olabilsin, çalan şarkıda alttan yukarı kaydırınca albüm üstünde lyrics açılsın, cihazda yoksa internetten ara. API: extras/lyrics/Lyrics.kt (music-deep çağırır).
10. [spotify] Spotify istemcisi gibi (resmi API/App Remote ile giriş, arama, çalma listeleri, şarkı video önizlemesi). Spotify'dan DRM'li ses indirme/çekme YAPILMAYACAK (reddedildi).
- [noise-ui] WhiteNoiseHub: satir = ikon + ad + tek ince cizili slider (dokun=ac/kapat, slider=seviye+ac); ust ana ses ve satir anahtari kaldirildi. Ayarlar (katlanir): zamanlayici+yavas kisma (NoiseTimer), ana ses, karisim kaydet/adlandir/sil, ses sifirla, arka planda cal; Tema galerisi 72 gorunum (12 palet x 6 stil, Winamp dahil, NoiseThemes.kt, pref noise_ui). derlenmedi
- [noise-ui] NoiseWidget: ayni tema prefi ile 72 gorunum (bitmap cizimli arka plan/dugme, Winamp stili dahil); layout widget_noise_main yeniden yazildi. Manifest degisikligi yok. derlenmedi
- [lyrics] extras/lyrics: Lyrics.kt (Lyrics.forTrack/searchByText/forHit; mp3 USLT/SYLT, flac tag, sidecar, cache, LRCLIB+ovh), LyricsSearchLogic.kt (LrcLrc rules port), LyricsUi.kt (LyricsView, LyricsSearchScreen, lyricsSwipeUp, LyricsSheet), LyricsOverlay.kt (FloatingLyricsSearch, MiniLyrics, LyricsListenerService). API: .scratch/extra-apps/lyrics-api.md, manifest: needs-lyrics-spotify.md. derlenmedi
- [spotify] extras/spotify: SpotifyAuth (PKCE, SpotifyAuthActivity), SpotifyApi (Web API), SpotifyHub.Screen(onClose) (now playing + remote, search, playlists, liked, 30s preview, YouTube WebView video, import to Pulse playlist). Audio download/ripping not implemented. derlenmedi

## music-deep (derlenmedi)
- [music-deep] Motor: MusicSettings.kt (on-yükseltici, ReplayGain etiketten, parça arası fade, sessizliği atla, hız/ton, kütüphane: en kısa süre/gizli/dışlanan klasör, bildirim kapağı, uyku: dakika + parça sonu). AudioGain işlemcisi ses hattında. Kütüphane: tür/yıl/besteci alanları. Etiket düzenleme (MusicTags: başlık/sanatçı/albüm, başlığa dokun). derlenmedi
- [music-deep] Poweramp: kapak tam genişlik kare, kapak üstünde belirgin söz düğmesi + yukarı kaydırma (LyricsSheet), Albümler/Sanatçılar/Türler/Yıllar/Besteciler listeleri, 54 görünüm (ThemeGrid, 24'ü paletlerden üretildi). derlenmedi
- [music-deep] Ayarlar: PlayerSettings.kt (katlanır bölümler, MusicHub ayar sayfasında), Winamp skin seçici ayarlarda yatay şerit, Winamp'ta söz düğmesi. Beyaz gürültü karıştırıcı (NoiseMixer, üst çubuktaki ses simgesi), söz arama, yüzen arama anahtarı, Spotify girişi. derlenmedi
- [music-deep] Çevrimiçi çalma listeleri: tam oynatıcı (ilk parça hemen, kalan arka planda kuyruğa, seek, kuyruk, karıştır/tekrar, widget ve arka plan aynı motor). derlenmedi
- [music-deep] Widget: MusicWidget + Square 2x2 + Big 4x3 + Slim 4x1, 72 tema (40 palet yuvarlak + 16 keskin + 16 hap), MusicWidgetConfig galerisi; Winamp widget config de ThemeGrid. Manifest satırları: .scratch/extra-apps/needs-music-deep.md. derlenmedi
- [music-deep] Eksik: gerçek çakışmalı crossfade (tek ExoPlayer, fade-out/in), ID3 yazma (etiket uygulamada tutulur), Material/WMP'ye özel ayar bölümü, lock-screen özel arayüz (medya oturumu). derlenmedi

## Yeni istek 3 (2026-10-18 gece, Pulse)
11. [icons-themes] Emoji/unicode simge (⏮ ⏭ vb.) yok; her temanın kendi vektör ikon seti olacak. Win95 teması kötü -> baştan düzgün yapılacak. Tüm temalar gözden geçirilecek (kötü/benzer olanlar elenecek).
12. [winamp-gaps] Winamp skinlerinde altta/üstte boşluk kalıyor: düzeltilecek, düzelmeyen skinler kaldırılacak.

- [winamp-gaps] Bosluk nedenleri: main.bmp 275x116 degilse blit hic cizmiyordu (bos bant); WinampMain artik blitFit ile tum main.bmp yi pencereye gerer (SkinDraw.kt). Widget zaten fitXY + Rect olcekli ciziyor (bosluk yok, 4x2 ve 4x1 agirliklari bitmap ile uyumlu), onizleme FillBounds. tools/validate_skins.py eklendi (PIL; main 275x116, cbuttons/titlebar/posbar/volume/numbers/text boyutlari, yakin kopya): base-2-91, lcars-rdt64, xmms-turquoise silindi, 75 skin kaldi (index.json guncel). derlenmedi
- [icons-themes] Yeni extras/ui/IconSet.kt: IconStyle (FILLED/ROUNDED/SHARP/LINE/PIXEL) + IconSet.vec/styleFor + TIcon; video skin (VSkin.icons), Poweramp (PowerampStyle.icons), Spotify, lyrics, LookChrome ikonlari vektor; emoji/unicode (⋮ ✕ ♪ ⏮ ⏭ |< >|) kalmadi. Win95 yeniden: MusicLook (C0C0C0, lacivert gradyan, piksel kapat), Poweramp Windows 95 skini, gurultu Win95 stili (NoiseLook style 6, widget bevel bitmap), muzik widget Win95 (widget_music_bg_w95.xml, WidgetShape.BEVEL). Tema temizligi: 11 muzik paleti, 10 video paleti yerinde yeni ayirt edilir renkle degistirildi, kontrast 4.5 duzeltildi, onAccent esigi .18; sayilar: muzik widget 73, poweramp 55, video 54, gurultu 73 (azaltma yok). Poweramp kok listeye En cok calinan/Son calinan/Son eklenen (Lib.Quick, Track.dateAdded + Library.kt cache/MediaStore DATE_ADDED). Not: Winamp/skin/ WinampWidget dokunulmadi; widgets/Widgets.kt ok ve store sayaci (non-music) ok metinleri kaldi. derlenmedi
- [notif] extras/music/notif/{NotifButtons,NotifProvider,NotifSettings}.kt + 25 ic_notif_* drawables: MediaStyle notification via DefaultMediaNotificationProvider subclass (user-ordered buttons, max 5 on Android, default prev/play/next/shuffle/repeat/close so close is the 6th and hidden until the user reorders), custom session commands (shuffle, repeat off/all/one, favourite heart, close = save queue+stop+clear+stopSelf, output speaker/headset/bluetooth via ExoPlayer.setPreferredAudioDevice, sleep, speed, rew10, fwd30; lyrics/noise/eq only open when app visible or overlay permission, extra notif_open needs hub support), content intent opens MusicLauncher, small icon, POST_NOTIFICATIONS request and Notification section in music settings, pause-on-unplug setting (setHandleAudioBecomingNoisy). Audio focus + ducking are ExoPlayer defaults, bluetooth/headset double=next triple=previous is Media3 default. Manifest: .scratch/extra-apps/needs-notif.md. Widgets not done. derlenmedi
- [video-settings] Video ayarlari yeniden yazildi (VideoSettings.kt, SettingsSection/SwitchRow/SliderRow): Oynatici, Hareketler, Ses, Altyazi (canli onizleme), Cozucu, Ekran, Kutuphane, Ag, Gizlilik, Yedek. Yeni: VideoExtras.kt (ag/tampon/onbellek/UA/zaman asimi, tunel, async codec, stereoya indirme, ses siniri, perde duzeltme, aramada durdurma, yenileme hizi eslestirme, HDR cikisi, sabit parlaklik, ses hatirlama, saat/pil), VideoLock.kt (biyometrik/ekran kilidi). Altyazi font/renk/kenar/arka plan/konum/kenar bosluk. JSON yedek/geri yukle, hepsini sifirla, gizli mod, gecmis temizle, kutuphane: en kisa video, gizli goster, yalniz secili klasorler, son liste uzunlugu. VideoPlayer.kt kucuk duzenlemeler (applyDecoder/net, restoreVolume, applySubPrefs, hassasiyet, VideoExtras/VideoStatus cagrisi). Baglanmayan: ses gecikmesi, ses normallestirme, altyazi varsayilan gecikmesi, gercek HDR tone-map (yalniz HDR cikis modu), varsayilan zoom. derlenmedi

- Pulse: Beyaz gürültü artık müzik çaların içinde (ses ikonu -> WhiteNoiseHub tam ekran); ayrı launcher/ayar/mağaza satırı kaldırıldı. derlenmedi, sonraki derlemede.
- Pulse (derlenmedi): ses paketleri budandı (ambient 36->17, kuş 34->5, ham WAV yedekleri silindi, Downpour/white/pembe/konuşma vb. gitti); döngü sonları 3 sn çapraz geçişle dikişsiz; kuşlar artık rastgele 8-15 sn kısa seslenme + 20-50 sn sessizlik; White Noise: önce tema listesi (Su, Doğa...) sonra seçenekler, sağ üst dişli = ayarlar, çip/pill yok; play/pause 3 sn fade; mağaza -> düz "Ek uygulamalar" listesi.
- Pulse (derlenmedi): Beyaz gürültü kartları: yuvarlak ikon düğmesi + ters-U yay ses ayarı (2 sütun); doku looplar ayna ile 2x uzun, katmanlarda rastgele ses salınımı + rastgele başlangıç; kuş 3.

## Pulse istek (2026-10-19, derlenmedi)
- [whitenoise] Müzik çaların üstünde "Beyaz gürültü" etiketli düğme (eski ikon etiketsizdi). Ambient servisi açılışta ses çalmıyorsa "çalıyor" bildirimini 5 sn sonra kapatır (MediaService.kt). derlenmedi
- [contacts] Samsung info (lookup/data URI) kimliği ctIdOf ile çözülüyor (ContactsAppUi.kt). derlenmedi
- [motion] Doğrusal ivme sensörü (TYPE_LINEAR_ACCELERATION) kullanılıyor; yerçekimi tahmini yavaş olduğu için sürekli ivme hareket vermiyordu (MotionCues.kt). derlenmedi
- [winamp-skin] Skin seçici 3 satır yatay ızgara, önizleme 210dp (WinampScreen.kt). derlenmedi
- [poweramp-anim] (ajan) Poweramp animasyonları. derlenmedi
- [lyrics-bold] (ajan) Sözler kalın, arama sonucu söz görünümü. derlenmedi
- Açık: Winamp sözler düğmesi skin içinde değil, sözler skin fontunda değil; Pulse Widgets sayfasında müzik/Winamp widgetları yok ("win" araması bulamıyor); her Winamp skini ayrı tam widget değil; tema sayısı (100 istendi) yok.
- [poweramp-anim-2] Poweramp çalar: kapak çapraz geçiş (Crossfade), oynat/duraklat ikonu çapraz geçiş, transport düğmelerinde basınca hafif küçülme, ilerleme çubuğu yumuşak geçiş (PowerampScreens.kt). derlenmedi
- Pulse lyrics: sözler kalınlaştırıldı (aktif satır Bold, diğerleri SemiBold); söz arama sonucu lyrics gibi ortalı gösteriliyor (derlenmedi)
- Pulse video: cevrimici altyazi arama (OpenSubtitles, extras/video/SubSearch.kt, API anahtari video ayarlarinda) - derlenmedi
- [ ] Pulse Winamp vis: Winamp-style attack/decay + peak caps (visual/VisState.kt), tap vis area for oscilloscope (prefs winamp_look vis_scope), shared WinampMain draw - derlenmedi
- [winamp-widget-fit] Winamp widget: pencere boyuta göre tam sığacak şekilde çiziliyor (letterbox, kırpma yok), 4x1 bantları kırpılmıyor; Widgets sayfasında Müzik başlığı altında Winamp ve Winamp 4x1 kartları ("win" araması); config önizlemeleri widget şekline göre. derlenmedi
- Pulse video: altyazı arama OpenSubtitles yerine anahtarsız yifysubtitles.ch ile (SubSearch.kt, ağırlıklı film) - API anahtarı alanı kaldırıldı - derlenmedi
- Pulse: Playlists launcher kaldırıldı, müzik çalara Playlists butonu eklendi (White noise yanında, Winamp hariç). derlenmedi
- [poweramp-sleek] Poweramp çalar sadeleştirildi (PowerampScreens.kt): kapak tam genişlik, başlık/sanatçı + flat favori/sözler, ilerleme, transport satırı (karıştır-önceki-oynat-sonraki-tekrar), ekolayzır/uyku/sayaç satırı; chip arka planları ve Pill kaldırıldı. Beyaz gürültü düğmesi MusicHub üst çubuğunda zaten var (çift düğme yok). derlenmedi
- Pulse Winamp: sözler düğmesi skinin başlık çubuğuna (LYRICS, text.bmp, x196-234 y0-14) taşındı, kitap satırı kaldırıldı; sözler skin fontuyla ana pencerenin altında (WinampLyrics.kt). LYRICS müzik görünümü: LyricsLookPage (MusicHub'da MusicLook.LYRICS dalı gerekli). derlenmedi
- [poweramp-bg] Poweramp: tüm sayfalar tam siyah (kenarlar dahil); yalnız çalma sayfasında albüm kapağı arka planı (ayar: Albüm kapağı/Siyah, önizlemeli, PlayerBgPicker). Çalma: Prev/Play/Next ortada, Shuffle/Repeat uçlarda; dalga formu ve süre Play altında ortalı. PowerampApp param adı skinStyle. derlenmedi
- [poweramp-player] Çalma ekranı: arka plan kapak bulanık+koyu (tam ekran); uyku tuşu sol slotta (Bedtime), yüzen panel: döner kadran 0-120 dk, Parça sonu, Kapalı; Lyrics yanında ⋮ menü (favori, etiket); simgeler ~- [poweramp-player] Çalma ekranı: arka plan kapak bulanık+koyu (tam ekran); uyku tuşu sol slotta (Bedtime), yüzen panel: döner kadran 0-120 dk, Parça sonu, Kapalı; Lyrics yanında ⋮ menü (favori, etiket); simgeler ~%17-18 küçük; dalga formu 40 kalın bar. derlenmedi
- Pulse: güncelleme denetimi (Updates.kt: açılışta 12 saatte bir GitHub releases/latest, "Yeni güncelleme var" penceresi, Sonra/İndir, sürüm hatırlanır; Hakkında'da "Güncellemeleri denetle" satırı) + Welcome yenilikler listesi ve ikon. derlenmedi
- [poweramp-cover] Poweramp: kapak sağ altında ⋮ (36dp) menü: Albüm kapağını internetten ara (iTunes, files/covers), Etiketleri düzenle, Favori; eski başlık menüsü kaldırıldı. Kapakta aşağı kaydırma artık hiçbir şey yapmıyor, yukarı = sözler. Dock: kayan gösterge + basma küçülmesi, dalga kalınlığı .95. derlenmedi

- Store: Fossify Messages built-in fallback: backup zip assets/extras/backup/fossify-messages.zip (single entry, exact-name match), Open button when installed. derlenmedi
- [look-isolation] Görünüm sızıntıları: ExtraToolActivity (ana süreçte) Look.resolved'u yazıp Pulse dönünce geri almıyordu, artık onPause/onResume ile her taraf kendi değerini geri alıyor (MainActivity + ExtraTool). Ayarlar ve görünüm anahtarları (Cfg/Opt) 'pulse_settings', Files görünümü 'files_look', Fotoğraflar 'photos_prefs' dosyalarında; ownPrefs ilk açılışta 'p'den bir kez kopyalar. Diğer süreçlerdeki eski 'p' kopyası artık Pulse temasını geri yazmaz. ExtraToolActivity taskAffinity com.umutk.pulse.tools. derlenmedi

## Pulse istek (2026-10-19 öğle, 0.10.118)
- [extras-external] Extra Apps mağazasında Müzik çalar, Video ve (yeni) Beyaz gürültü artık Pulse içinde değil, ayrı harici uygulama olarak açılır (MusicLauncher / VideoLauncher / WhiteNoiseLauncher activity-alias; Files/Photos ile aynı yol). Beyaz gürültü için launcher girdisi, Ayarlar > Ek uygulamalar listesine eklendi. Material görünümü aynı etkinlikten geldiği için ayrıca değişmedi. ExtraTool.kt, Tools.kt, StoreScreen.kt, MoreSettings.kt, AndroidManifest.xml. derlendi (0.10.118)
- [whitenoise-autoplay] Beyaz gürültü kendi kendine çalmıyordu: AmbientPlaybackService sistem yeniden başlatınca son karışımı yüklüyordu (onResume). Artık yeniden başlatmada karışım yüklenmez; çalan karışım yoksa servis hemen kapanır (MediaService.kt). derlendi (0.10.118)

## Pulse düzeltme (2026-10-19 öğleden sonra, 0.10.119)
- [crash-welcome] Açılışta çökme: Welcome.kt ve Updates.kt logosu painterResource ile yükleniyordu; mipmap ic_launcher bir adaptive-icon XML, Compose bunu vektör sanıp patlıyordu. imageResource'a geri alındı. derlendi (0.10.119)
- [signing] İmza değişti: /root/.android/debug.keystore 2026-10-19 12:33:58 tarihinde yeniden oluşmuş; önceki APK (0.10.116) imzası 2325036c... kayboldu, yeni APK farklı imzayla geliyor. Eski imza geri getirilemez. Bundan sonra aynı keystore kullanılır; üzerine kurulumda eski sürümü kaldırmak gerekebilir. derlendi (0.10.119)
- [data-backup] Ayarlar > Genel > Veri: dışa aktar / içe aktar (shared_prefs + files/ tek zip). İçe aktarma mevcut dosyaların üzerine yazar, sonra Pulse yeniden başlar. DataBackup.kt, MoreSettings.kt. derlendi (0.10.119)
- [files-root] Dosya yöneticisi kök/`/data/data` kısayolunu göstermiyordu: root durumu sadece Android/data gibi klasörlerde kontrol ediliyordu; artık açılışta kontrol ediliyor. Explorer.kt. derlendi (0.10.119)

## Yapılacaklar (2026-10-19 öğleden sonra, derleme yok)
- [ext-amoled] Dış uygulamaların varsayılan teması AMOLED siyah olsun (şu an Material). Müzik, video, beyaz gürültü, çalma listeleri (ExtraToolLook), Dosyalar, Kişiler, Takvim. CLAUDE.md'deki "Material is the DEFAULT look" kuralı da güncellenmeli.
- [files-scroll] Dosyalar: dosya yolu satırının altından kaydırınca dosya adları ve yazılar büyüyor. Düzeltilecek.
- [video-theme] Video oynatıcıda tema seçimi çalışmıyor.
- [lyrics] Şarkı sözleri tam değil: cihazdaki yerel .lrc dosyaları ve gömülü sözler görünmüyor (zaman damgalı, LRC). İnternetten arama isteğe bağlı olarak düşünülecek. Yer: MusicLyrics.kt.
- [music-all] Müzik kütüphanesine "Tüm şarkılar" kategorisi eklenecek.
- [music-bars] Müzik oynatıcı ekranında ses çubukları daha uzun olacak. Play / önceki / sonraki düğmeleri çubukların üstünde, tek satır olacak (şu an iki satır).
- [music-eq] Ekolayzır düğmesi oynatıcı ekranından kalkacak; medya oynatıcıların altında zaten var.
- [music-top] Beyaz gürültü ve Çalma listeleri çipleri oynatıcı üst çubuğundan kaldırıldı (MusicHub.kt, derlenmedi). Yer değiştirme: Ayarlar ya da kütüphane.
- [art-layer] Oynatıcıda "Sanat arka planı" ayarı kapalıyken sadece arka planda görünüyor, ama oynatıcı ekranında ekstra bir katman (siyah/yarı saydam) var. Ayar açılınca ayrı katmanlar geliyor. Tek katman olmalı: arka planda albüm kapağı, önde içerik.
- [music-bg] Oynatıcı ekranı siyah üst katman gibi duruyor; doğrudan tam ekran olmalı.
- [winamp-turkish] Winamp Türkçe karakterleri (ş, ğ, ı, İ, ö, ü, ç) göstermiyor. Font sorunu.
- [music-widget] Müzik widget'ı "music" yazıyor ve tüm temalara aynı tasarımla eklenmiş. Her görünüme (Material, Poweramp, Winamp, WMP, Windows) kendi tasarımına uygun widget olacak. Material/basic; eski uygulamalardaki widget'lara bakılacak.
- [music-cover] Albüm kapağı sadece oynatıcıda görünüyor; kütüphanede de (Material, Poweramp ve diğer görünümlerde) görünmeli.
- [music-swipe] Yatay kaydırmada albüm kapakları üst üste dizilmiş gibi kayıyor. Bir sonraki kapak yandan gelen carousel gibi olmalı (kapak kaydırmada doğru yönde animasyon).
- [wn-layout] Beyaz gürültü ekranı çok kötü. Kategoriye girmeden tüm sesler doğrudan görünsün; üstte presetler (okyanus vb. — önceki haliyle güzeldi) olsun.
- [wn-settings] Beyaz gürültü ekranındaki Ayarlar (dişli) düğmesi kaldırılacak.
- [wn-winamp-gear] Winamp görünümüne bir çark (ayarlar) düğmesi eklenecek; diğer görünümlerde zaten var, oradan ayarlara girilebilmeli.

## Düzeltmeler (2026-10-19, derleme yok, kullanıcı derle demeden)
- [files-scroll] Dosya yolu satırı: sürükleme artık satırın dışına çıkınca boyutu değiştirmiyor (Explorer.kt sizeDrag). derlenmedi
- [winamp-turkish] Winamp text.bmp'de olmayan ş/ğ/ı/ü/ç harfleri en yakın ASCII harfe düşürülüyor (SkinDraw.kt skinGlyph). Gerçek Türkçe için skin fontu gerekir. derlenmedi
- [music-eq] Poweramp çalar: ekolayzır düğmesi kaldırıldı (PowerampScreens.kt). derlenmedi
- [wn-settings] Beyaz gürültü: dişli düğmesi kaldırıldı (WhiteNoiseHub.kt). derlenmedi
- Açık kalanlar: ext-amoled, video-theme, lyrics (.lrc), music-all, music-bars, art-layer, music-bg, music-widget, music-cover, music-swipe, wn-layout, wn-winamp-gear
- [poweramp-screenshot] Çalma ekranı referans ekran görüntüsüne göre: başlık ve sanatçı yuvarlak kutularda, süre çubuğun iki ucunda (PowerampScreens.kt). derlenmedi
- [wn-layout] Beyaz gürültü ana ekranı: kategori satırları ve Karışımlar kaldırıldı; üstte presetler (NChip, yatay kaydırma), altta tüm sesler CALM_ORDER sıralı iki sütun. WhiteNoiseHub.kt. derlenmedi
- [wn-settings] Dişli kaldırıldı ama artık Ayarlar paneline (zamanlayıcı, kısma, ana ses, tema) giden hiçbir yol yok (settingsOpen hiç true olmuyor). Bir giriş noktası MoreSettings/Menus tarafında gerekli. WhiteNoiseHub.kt. PARTIAL, derlenmedi
- [wn-winamp-gear] Winamp için çark: beyaz gürültü üst çubuğunda hiçbir görünümde dişli yok (wn-settings ile kaldırıldı); Winamp dosyalarına dokunulmadı. Karar gerekli: dişli geri mi eklensin? PARTIAL, derlenmedi
- [wn-look] Beyaz gürültü AMOLED: uygulama görünümünde T.bg (#000) ve T.text; Amoled paleti #000000 üstten #0A0A0A alta gradyan. Cihazda doğrulanmadı. NoiseThemes.kt değişmedi. derlenmedi
- [wn-settings-entry] Beyaz gürültü ayarları artık listenin altındaki 'Ayarlar' satırından açılıyor (dişli yok). derlenmedi
- [winamp-gear] MusicHub top bar gear already covers Winamp (no change; derlenmedi)
- [music-widget] per-look widget design (MusicWidget.lookTheme), Look item in config; layouts still need resize (derlenmedi)
- [winamp-turkish-check] WinampWidget.textCell falls back to skinGlyph (ş ğ ı İ ü ç Ö) (derlenmedi)
- [lyrics-live] Senkronize sözler: aktif satır tam renk ve büyük, diğerleri soluk; satır değişince animasyonla ortaya kayar (LyricsPanel.kt LyricsBody). derlenmedi
- [video-theme] Tema seçimi: önizleme içindeki düğmeler ve süre çubuğu tıklamayı yutuyordu (VideoSkins.kt SkinPreview/SkinBtn/SkinSeek live=false); artık kart tıklanınca seçiliyor. derlenmedi
- [video-tap-bar] Üst/alt çubuğun boş alanına dokunmak videoya gidip çubuğu gizliyordu; çubuklar dokunuşu yutuyor (VideoPlayer.kt). derlenmedi
- [video-settings] Ayarlarda listelenen seçeneklerin çoğu zaten var; eksik: hız varsayılanı, altyazı gecikmesi kalıcı değil, yakınlaştırma kalıcı değil (VideoPrefs anahtarı için VideoLibrary.kt gerekir). derlenmedi
- [video-theme-all] Görünüm farkı yapılmadı: AMOLED/siyah kuralı kontrol edildi (Mono, Terminal, amoled, minimal, mx, winamp, poweramp saf #000000). Görsel yeniden tasarım cihaz doğrulaması gerektirir. derlenmedi
- [fossify-integrate] Fossify APK/zip silindi, assets/extras/manifest.json boş, StoreModules.kt BUILT_IN_MODULES filtresi; Tools.kt MessagesScreen (SMS liste/thread/gönder, izin) eklendi; Menus.kt girişi ve AndroidManifest SMS izinleri bekliyor (derlenmedi)
- [store-remove-extras] Mağaza: Fossify satırı kaldırıldı (filtre + boş manifest); StoreScreen.kt tarafı ve canlı GitHub manifesti hâlâ kontrol edilmeli (derlenmedi)
- [artcraft] Okuma ile neden bulunamadı; logcat (FATAL EXCEPTION, ArtCraft, :artcraft) gerekli (derlenmedi)
- [ext-amoled] ExtraToolLook varsayılanı AMOLED oldu (ExtraTool.kt); CLAUDE.md 'Material is the DEFAULT' satırı güncellenmeli (derlenmedi)
- [messages-entry] Menü Araçlar > Mesajlar girişi ve SMS izinleri manifeste eklendi. derlenmedi
- [music-all] Material: sekme 'Şarkılar' -> 'Tüm şarkılar' (MaterialPlayer.kt); Poweramp zaten vardı; WMP düz liste, kategori yok (MusicHub.kt). derlenmedi
- [music-cover] Kapaklar kütüphane satırlarında: Poweramp TrackRow ve albüm/sanatçı satırları (PowerampLists.kt), WMP ağaç listesi (MusicHub.kt); Material zaten gösteriyordu. Winamp liste penceresi kapsam dışı (WinampPlaylistWindow.kt). derlenmedi
- [music-swipe] Kapaklarda yatay kaydırma carousel: komşu kapak yandan parmakla gelir (CoverCarousel, TrackArt.kt; Poweramp NowPlayingScreen ve Material). derlenmedi
- [music-bars] Poweramp ses çubuğu 28dp -> 40dp, transport alanı 96dp -> 68dp (PowerampScreens.kt). KISMEN: tek satır zaten vardı, cihazda kontrol edilmeli. derlenmedi
- [art-layer] Sanat arka planı kapalıyken Material oynatıcıda yarı saydam katman kaldırıldı; açıkken tek katman (bulanık kapak), Poweramp ve MusicHub'daki siyah örtüler kaldırıldı (MaterialPlayer.kt, PowerampApp.kt, MusicHub.kt). derlenmedi
- [music-bg] Material tam ekran oynatıcı: üst başlık ve iç boşluk oynatıcı açıkken gizleniyor (MusicHub.kt nowPlaying, MaterialPlayer onNowPlaying). derlenmedi
- [ext-amoled-music] Müzik Material oynatıcı AMOLED'de saf #000000 (MaterialPlayer.kt pbg). derlenmedi
- Video skin gallery: family title row (Next, Material, Win 95/XP/7/10/11, Media Player 11, VLC, MX, Winamp, Poweramp, Minimal, AMOLED, Palettes) filters the grid; current skin stays highlighted (VideoSkins.kt) - derlenmedi
- Video skin gallery: 16 near-duplicate palettes removed from the gallery (40 -> 24), VideoWidgetLooks unchanged (VideoSkins.kt) - derlenmedi
- Video winamp skin refined: dark grey-blue body, green LCD text, metal bevelled buttons (VideoSkins.kt) - derlenmedi
- Video poweramp skin refined: charcoal body, orange accent, tonal round buttons, bar+handle seek (VideoSkins.kt) - derlenmedi
- Video AMOLED family: pure #000000 skins (AMOLED, Mono, Terminal) grouped in the AMOLED family; Minimal has its own family (VideoSkins.kt) - derlenmedi
- [poweramp-rating] Kapak sol altında tek yıldız; basılı tutunca 5 yıldız açılır, kaydırarak puan verilir; dokununca 5 ya da 0. Kapak menüsünden favori kaldırıldı (MusicStats.ratings/setRating). derlenmedi
- [poweramp-settings] Ayar düğmesi Poweramp'ta sekme çubuğunun sağ ucunda; üst dişli o görünümde gizli. MiniBar kaldırıldı. derlenmedi
- [library-cats] Besteciler ve klasör ağacı varsayılan gizli; 'Daha fazla kategori' satırıyla açılır. derlenmedi
- [installer-apks] Play .apks paketleri (base.apk + split_*.apk, toc.pb yok) APKS olarak tanınıyor; split listesi görünüyor. derlenmedi
- Files: secim cubugunda Info yerine Kes (Material/AMOLED'da kes ikonu eksikti, Info cikiyordu; EX_MATERIAL'a cut eklendi) - derlenmedi
- Files: AMOLED ucnokta menusu saf siyah (filesLook panel #000000) - derlenmedi
- Files: yol cubugunda segmentler arasina chevron (ExCrumbs, tum looklar) - derlenmedi
- Files: secim menusune tek arsiv icin Arsivi ac/Buraya cikar/Arsive ekle menu girisleri eklendi - derlenmedi
- [installer-auto] Bölümlü paketler (apks/xapk/apkm/zip) 'none' modunda artık sistem oturumuyla otomatik kurulur, hata vermez. derlenmedi
- [files-zoom] Yol satırına büyütme (zoomIn) ve küçültme (zoomOut) düğmeleri eklendi; kaydırma jesti de duruyor. derlenmedi
- [video-next-speed] Varsayılan oynatma hızı ayarı (Oynatıcı > Varsayılan hız); 'Hızı hatırla' kapalıyken başlangıç hızı bu olur (VideoSettings.kt, VideoPlayer.kt, VideoLibrary.kt) - derlenmedi
- [video-next-pan] Yakınlaştırılmış videoda iki parmakla kaydırma (pan); yakınlaştırma 1x olunca sıfırlanır (VideoPlayer.kt) - derlenmedi
- [video-next-skin] Next kılıfı Next Player'ın Material 3 koyu renkleri ve 20 dp kontrol köşeleri (VideoSkins.kt) - derlenmedi
- [music-theme-families] Müzik tema seçici MusicHub.kt MusicThemeGallery: üstte aile başlıkları (Material, Poweramp, Winamp, Windows Media Player 7, Lyrics, Koyu, Retro, AMOLED siyah), altta yalnız seçilen ailenin temaları; WMP 7 skin seçimi eklendi. Poweramp skin listesinde tekrarlanan skinler kaldırıldı, PowerampSkins.kt powerampFamily. derlenmedi
- [winamp-classic] Winamp ailesinin ilk teması Klasik (gri-mavi gövde, yeşil LCD, pah kenarlı tuşlar); yüklü skin olmasa da görünür (WinampScreen.kt SkinStrip). derlenmedi
- [poweramp-refine] Poweramp Material skini Poweramp kaynak APK renklerine göre (#212121, #303030, #424242, ikincil metin %70 beyaz / açık temada %54 siyah), PowerampSkins.kt. derlenmedi
- [library-cat-chips] Müzik kütüphanesi kategori çip satırı (PowerampLists.kt CatChips): Albümler, Sanatçılar, Türler, Yıllar, Klasörler; Besteciler ve klasör ağacı 'Daha fazla kategori' ile açılır. derlenmedi
- [files-select-shape] Dosyalar seçili satır kapsül (yarım daire uçlu), ızgara hücreleri yuvarlak, aralar daraltıldı (Explorer.kt). derlenmedi
- Ek uygulama ayarları: anahtarlar AMOLED'de beyaz blok değil, siyah zeminde açık çerçeveli (Controls.kt PSwitch); kaydırıcı sürükleme artık en güncel geri çağırma ve aralığı kullanır (Controls.kt PSlider, rememberUpdatedState) — derlenmedi
- [files-selbar] Seçim ikonları 28dp; Windows temalarında üst şeridin arka planı kaldırıldı (Material'da kalır). Seçim çubuğu alta taşınmadı (sel body içinde). derlenmedi
- [poweramp-now-playing] Poweramp şimdi çalan ekranı: kenar boşluğu 16 dp, puan yıldızı/menü/Sözler hedefleri en az 40 dp, başlık ve sanatçı çipleri aynı şekil ve iç boşlukta, süre satırı seek çubuğu kenarlarına hizalı, parça sayacı ortada, uyku diyaloğu satır dışına alındı (PowerampScreens.kt NowPlayingScreen, LyricsPill, RatingStar, TrackMenu) - derlenmedi
- [poweramp-tabbar] Poweramp sekme çubuğu göstergesi 8 dp yatay ve 4 dp dikey kaymıştı; iç boşluk kaldırılıp 48 dp satır tek kökenli yapıldı (PowerampScreens.kt PowerampTabBar) - derlenmedi
- [poweramp-settings-btn] Poweramp ayarlar düğmesi: pill sekmelerin yanında 48 dp daire, düz çubukta sonda (PowerampApp.kt PowerampApp) - derlenmedi
- [poweramp-search-clear] Poweramp arama 'temizle' simgesi 40 dp dokunma alanına alındı (PowerampLists.kt) - derlenmedi
- [winamp-layout] Winamp ekranı: yan kenar boşluğu 8 dp, pencere genişliği boşluk içinde (WinampScreen.kt WinampScreen); kontrol dokunma alanları ~40 dp'ye yaklaştı (SkinDraw.kt Zone.padX, WinampPlayer.kt MAIN_ZONES, WinampEqWindow.kt zones, WinampPlaylistWindow.kt zones) - derlenmedi
- [winamp-a11y] Winamp kontrolleri için erişilebilirlik: pencere adı ve her düğme için TalkBack eylemi (SkinDraw.kt skinActions, WinampPlayer.kt WinampMain, WinampEqWindow.kt, WinampPlaylistWindow.kt, WinampLyrics.kt) - derlenmedi
- [winamp-skin-kbps] Varsayılan Winamp skininde KBPS/KHZ yazıları kutularının dışındaydı; kutuların içine alındı (DefaultSkin.kt paintMain) - derlenmedi
- [poweramp-wave-row] Dalga formu play tuşunun yanındaki satıra alındı (transport kutusunun arkasında), alt alta değil. derlenmedi
- [artcraft-list] ArtCraft: son sürüm APK'ları sürüm numaralı (cadcraft-0.9.0.apk); filtre -release.apk olduğu için liste boştu. Artık .apk kabul ediliyor, uygulama adı sürümden ayrılıyor. derlenmedi
- [widget-default-look] Widget'lar, kendi stilini seçmediğinde Pulse uygulamasının kayıtlı temasını izler (Windows 11/10/7/XP/95, AMOLED; diğerleri Material) - WidgetStyles.default (widgets/Widgets.kt) - derlenmedi
- [music-widget-windows-look] Müzik widget'ı, Müzik ekranında look seçilmemişse Pulse'un Windows temasını (95/XP/7/10/11, AMOLED) kullanır; Material ve Lyrics sistem renkleri kalır (widgets/music/MusicWidget.kt appLookTheme) - derlenmedi
- [office-scan] Office: Belge tara (DocScan.kt DocScanPage: kamera sayfaları, gri+kontrast, PDF/PNG Download/Pulse'a); giriş OfficeViewer.kt OfficePage (Dosya aç yanı) - derlenmedi
- [notif-history-store] Bildirim geçmişi: NotifHistListener + NotifHist (extras/notifhist/NotifHistStore.kt), JSON dosyası en fazla 500 kayıt, yazma IO şeridinde, Pulse ve süreli (ongoing) bildirimler atlanır, manifest girişi eklendi - derlenmedi
- [notif-history-screen] Bildirim geçmişi ekranı NotifHistoryScreen(back) (extras/notifhist/NotifHistScreen.kt): güne göre gruplu, arama, tümünü sil (onaylı), izin kartı, uygulama ikonu, dokununca uygulamayı açar - derlenmedi
- [pomodoro-mode] Zamanlayıcıya Pomodoro modu eklendi (çalışma 25 / kısa mola 5 / 4 çalışmadan sonra uzun mola 15 dk; başlat, duraklat, atla, sıfırla; döngü sayacı). features/PomodoroClock.kt, TimerTool içinde. derlenmedi
- [pomodoro-persist] Pomodoro durumu ayrı "pomodoro" SharedPreferences dosyasında, bitiş zamanı duvar saatine göre tutuluyor; ekrandan çıkınca da sürüyor. derlenmedi
- [timer-clock-skins] Zamanlayıcı yüzleri: rakam (eski), halka, kalın rakam, domates (sıvısı azalan, yapraklı). features/PomodoroUi.kt TimerFace. derlenmedi
- [timer-skin-row] Yüz seçici: yan yana önizleme satırı (SkinRow), seçim "timer" dosyasında skin anahtarında. derlenmedi
- Veri yedeği: tüm shared_prefs dosyaları ve files/ kullanıcı verisi dışa aktarılıyor; cache, indirmeler ve Spotify/AppLock/TOTP gizli dosyaları atlanıyor (DataBackup.kt) — derlenmedi
- Veri yedeği: dışa aktarmadan önce dosya sayısı ve boyutu gösteriliyor (MoreSettings.kt Data kartı) — derlenmedi
- Veri yedeği: içe aktarmadan sonra geri yüklenen ayar ve dosyaları listeleyen mesaj, sonra yeniden başlatma (MoreSettings.kt) — derlenmedi
- Ekran kaydı: MediaProjection + MediaRecorder servisi, Movies/Pulse Recordings (MP4, MediaStore), bildirimde Durdur, isteğe bağlı mikrofon (extras/screenrec/ScreenRecService.kt) — derlenmedi
- Ekran kaydı: hızlı ayar kutucuğu (ScreenRecTile) ve onay/durum penceresi (ScreenRecActivity, saydam, AMOLED siyah) — derlenmedi
- Ekran kaydı: Tools.kt girişi ve TileActions.kt kutucuk kimliği sahibi tarafından eklenecek (satırlar rapor edildi) — derlenmedi
- Ekran kaydı: AndroidManifest izinleri, servis, aktivite ve kutucuk kaydı eklendi — derlenmedi
- [extra-voice] Ses kaydedici: MediaRecorder AAC/MP4 44.1 kHz, kayit/duraklat/durdur, kayit listesi (oynat, yeniden adlandir, sil - ada gore onay), Music/Recordings MediaStore, gecen sure; extras/voice, Tools.kt 'voice', VoiceLauncher alias. Eksik: RECORD_AUDIO manifest izni ve ExtraTool.kt routeOf eşlemesi (VoiceLauncher -> voice) henuz yok - derlenmedi
- [extra-notes] Notlar: once yoktu; baslik+govde liste, olustur, duzenle, sil (onayli), arama; JSON dosyasi filesDir/notes/notes.json (extras/notes), Tools.kt 'notes', NotesLauncher alias. Eksik: routeOf eşlemesi (NotesLauncher -> notes) ExtraTool.kt'de - derlenmedi
- [extra-weather] Hava durumu: Open-Meteo (anahtarsiz) anlik sicaklik, durum, ruzgar, nem, 5 gunluk liste; cihaz konumu (ACCESS_COARSE_LOCATION, calisma aninda istenir) ya da sehir arama (geocoding); son sonuc onbellekte (extras/weather), Tools.kt 'weather', WeatherLauncher alias. Eksik: routeOf eşlemesi (WeatherLauncher -> weather) ExtraTool.kt'de - derlenmedi
- [live-lyrics] Canlı sözler: oynatıcıda (LiveLyricsLine, kapak altı), yüzen pencerede (MiniLyrics, izin varsa), bildirimde (NotifProvider, 2 sn) ayrı ayrı açılıp kapanır; ayarlar LiveLyricsSettingsSection, prefs lyrics_live. Oynatıcı ve ayar satırları henüz bağlı değil (MusicHub/PlayerSettings). derlenmedi
- Files: paste status shows 'Pasted N items' (copy keeps clipboard) or 'Moved N items' (cut clears it), in Explorer.kt doPaste — derlenmedi
- [launcher-plan] Pulse ana ekran (launcher): plan scratchpad/launcher/PLAN.md. Referans: Launcher95, Win10 Launcher, Nova yedeği. Lisans kararı bekleniyor (Lawnchair Apache-2.0 referans; GPL-3 kodu kopyalama). derlenmedi
- [launcher-phase1] HOME aktivitesi (singleTask, opak launch teması), dikey ızgara, PackageManager uygulama listesi, sürükle-bırak, Pulse prefs kalıcılığı. Widget yok. derlenmedi
- [launcher-phase2] Yatay (landscape) mod: ayrı ızgara boyutu, sol kenarda görev çubuğu, dönünce ikon yeniden yerleşimi. derlenmedi
- [launcher-looks] Her look için görev çubuğu, Başlat düğmesi, masaüstü ızgarası ve ikonlar: Win11 (Mica), 10, 7, XP, 95, Material, AMOLED. 7/XP gradyan kararı bekleniyor. derlenmedi
- [launcher-widgets] Widget listesi: saat, takvim, hava, pil, ağ, arama, uygulama, klasör, notlar, son dosyalar, fotoğraf, kişiler, pomodoro, oynatıcılar (Winamp/Poweramp/Next: tam ve kırpılmadan), AppWidgetHost. derlenmedi
- [launcher-units] Birim ayarları: sütun/satır (dikey ve yatay ayrı), hücre ölçeği, ikon boyutu, etiket boyutu, kenar boşlukları, görev çubuğu yüksekliği/konumu, dock öğe sayısı, widget span, 12/24 saat, C/F. Bölümler daraltılabilir. derlenmedi
- Pulse Launcher core (derlenmedi): LauncherCore (types, LauncherLook, LauncherLooks), MaterialLauncherLook (Material + AMOLED), LauncherActivity (HOME/LAUNCHER, :launcher process, grid or pager), LauncherPrefs, LauncherSettings; Tools.kt launcher entry, Menus.kt Launcher entry, manifest LauncherActivity.
- XP launcher look (extras/launcher/XpLauncherLook.kt): taskbar 30dp solid #245EDC, green Start, tray #0997FF, start menu two columns, solid wallpaper - derlenmedi
- Win11 launcher look (Win11LauncherLook.kt, id win11): derlenmedi. Registry kaydı sahibinde.
- [launcher-look-win95] Win95LauncherLook (id win95): 30 dp taskbar, Start 75 dp, clock 75 dp sunken, bevels drawn with lines, teal desktop, 75 dp icon cells; registry entry not added yet. derlenmedi
- Win7 launcher look (win7): Win7LauncherLook.kt yazildi, Canvas orb, solid renkler; LauncherLooks.all kaydi eklenmedi (registry yasak) - derlenmedi
- Win10 launcher look (Win10LauncherLook.kt, id win10): taskbar 45dp, 35dp icons, clock, Start panel 350x450, 86dp tiles, solid colours; not registered in LauncherLooks, derlenmedi
- Poweramp parity: Effects tab gets a Reverb page (mix, size, damp, pre-delay) on EnvironmentalReverb, saved and re-attached per audio session (poweramp/PowerampReverb.kt, PowerampEffects.kt ReverbPage, PowerampApp.kt listener) - derlenmedi
- Poweramp parity: cover menu gets Audio info dialog (tags, length, folder, file, size) (poweramp/PowerampScreens.kt TrackMenu, AudioInfoDialog) - derlenmedi
- [poweramp-library-whitenoise] Poweramp library quick items: 'White noise' entry (Lib.WhiteNoise, PowerampLists.kt LibraryScreen) opens WhiteNoiseHub inline - derlenmedi
- [poweramp-library-all-sounds] Poweramp library quick items: 'All sounds' entry (Lib.AllSounds) opens WhiteNoiseHub; the hub has no categories, so it is the same flat list - derlenmedi
- [poweramp-library-online-playlists] Poweramp library quick items: 'Online playlists' entry (Lib.OnlinePlaylists) opens OnlinePlaylistsHub inline - derlenmedi
- [launcher-dock] Ana ekran dok: LauncherPrefs.dock (virgülle paket listesi, en fazla 5; boşsa ilk 3 uygulama, dockApps LauncherCore.kt), Win11/10/7/95/XP ve Material/AMOLED görev çubuğu dok uygulamalarını gösterir, LauncherSettings'te Dok bölümü + en fazla 5 seçimli liste. derlenmedi
- [build] Pulse 0.10.120 APK derleme başlatıldı (launcher, dock, poweramp, white noise, notifhist, screenrec, voice/notes/weather). derlenmedi
- [launcher-material-nova-dock] Material launcher taskbar: Nova-style bottom row (no pill), drawer button left, up to 5 dock apps with 48dp icons and labels evenly spaced on the wallpaper, MaterialLauncherLook.kt Taskbar - derlenmedi
- Pulse: Tools menüsündeki Messages, Launcher, Notification history, Screen recorder kaldırıldı; Ek uygulamalar mağazasına (BuiltinRows) Messages, Launcher, Notification history, Screen recorder, Voice, Notes, Weather eklendi. Messages/Launcher/Notification history/Voice/Notes/Weather yerleşik sayfa olarak açılıyor (bağımsız activity değil). derlenmedi
- Notification history: kapalı varsayılan; Ek uygulamalar mağazasında anahtar; açılınca ⋮ Tools menüsünde 'i' ikonlu giriş; onPosted açık değilken kaydetmez. Her tema için ayrı ikon henüz yok (messages/notifications/record/voice/weather için). derlenmedi
- Files: yapıştır FAB'ı artık clip varken görünüyor; aynı klasöre kesme 'Zaten bu klasörde' notu veriyor. Açıp kapatınca görünme sorunu cihazda doğrulanmadı. derlenmedi

- Ikonlar (messages, notifications, record, notes, voice, weather): win95/xp/win7 PNG (48px), win11/oneui/coloros PNG (96px), mat_*.xml, FluentPaths (Win10). Derlenmedi.
- Music: kapak kaydırma tek eased tween (spring yok, sürükleme yarışı yok); Poweramp şarkı sözleri ikon olup kapağın sağ altında (üç nokta sola kaydı). derlenmedi
- [derlenmedi] Weather: home widgets (small 2x2, wide 4x2), hourly, 7-day, feels-like, wind dir/gusts, pressure, cloud, UV, sunrise/sunset, moon, AQI, graphs, units, multiple cities, severe-weather alert (off by default). Files: extras/weather/*, widgets/WeatherWidgets.kt, res/xml/widget_weather*.xml, manifest receivers, WidgetsPage + Widgets.kt registration.
- Voice recorder: widget (2x1 one-tap record), playback seek/speed/skip/loop/next/sleep timer/resume, share, duplicate, export, search, sort, favourites, auto-stop, countdown, quality, live waveform, keep-screen-on, stop on leave. derlenmedi (no build run).

- Pulse music widgets: Poweramp widget, Winamp visualizer widget, one picker card per Winamp skin (WinampSkinPick), Poweramp/visualizer picker cards. Files: extras/music/widgets/*, res layout widget_poweramp / widget_winamp_vis, manifest receivers. derlenmedi
- Poweramp: üç nokta artık kapağın sağ altında, dokununca ortada panel (kapak, başlık, sanatçı, şarkı sözleri, kapak bul, etiketleri düzenle, ses bilgisi). derlenmedi
- Launcher: settings reachable (store row, Settings > Extra apps, menu, gear on home); prefs in file (fix: launcher process cached SharedPreferences, look change invisible); hidden apps, sort, long-press sheet, dock on/off, drawer search, clock, portrait lock, full screen, default home, reset, Pulse themes as looks. derlenmedi

## Pulse: notification history + screen recorder (derlenmedi)
- Notification history: saving switch and access card on the page; App info button; rebind on switch on; BackHandler; duplicate import removed. Device check: restricted settings (Android 13+).
- Screen recorder: new hub (Tools route screenrec, Store row opens it), pause/resume, timer, mic, quality, fps, countdown, time limit, recordings list (play, share, rename, delete, search, sort).

Screen recorder feature list (55 candidates)
1 Start from the app (done)
2 Start/stop from Quick Settings tile (done, existing)
3 Stop from notification (done, existing)
4 Pause and resume from app and notification (done)
5 Live status Ready/Starting/Recording/Paused (done)
6 Elapsed timer excluding paused time (done)
7 Notification chronometer (done)
8 Microphone on/off, permission asked first (done)
9 Quality presets 720p/1080p/native (done)
10 Frame rate 30/60 (done)
11 Bitrate scales with size and fps (done)
12 Countdown before start (done)
13 Time limit auto-stop 5/10/30 min (done)
14 Free-space readout, low-storage colour (done)
15 Recordings list from MediaStore, newest first (done)
16 Date, duration, size per recording (done)
17 Search by name (done)
18 Sort newest/name/size/length (done)
19 Play in another app (done)
20 Share via chooser (done)
21 Rename keeping .mp4 (done)
22 Delete with confirmation (done)
23 Settings remembered (done)
24 Discard recording that never started or failed to stop (done)
25 Saved to Movies/Pulse Recordings (done, existing)
26 Clean stop when capture is revoked (done, existing)
27 EN/TR text (done)
28 List refresh after each recording (done)
29 Device internal audio: AudioPlaybackCapture + own muxing; untestable here, apps can opt out (backlog)
30 Start from notification: needs a persistent idle notice, conflicts with the privacy rule (backlog)
31 Own look for the recorder: needs a standalone activity; nested LookHost swaps global Look.resolved (backlog)
32 Home-screen widget: RemoteViews cannot follow the look system; needs per-look layouts (backlog)
33 Paused state in the Quick Settings tile (backlog)
34 Show touches: needs WRITE_SECURE_SETTINGS, not possible for normal apps (backlog)
35 Front-camera picture-in-picture (backlog)
36 On-screen drawing/annotation with overlay (backlog)
37 Text/timestamp watermark: needs frame compositing (backlog)
38 Trim/cut recordings: re-encode path (backlog)
39 Convert to GIF (backlog)
40 Multi-select and bulk delete (backlog)
41 Group recordings by day (backlog)
42 Save-location chooser via SAF tree (backlog)
43 Auto-delete old recordings (backlog)
44 Auto-stop at low storage: needs polling in the service (backlog)
45 Pause when screen turns off (backlog)
46 Do-not-disturb during recording: needs notification policy access (backlog)
47 Rotation handling mid-recording (backlog)
48 Crash-safe fragmented MP4: MediaRecorder cannot write it; not possible with this API (backlog)
49 Audio level meter (backlog)
50 Start/stop sound or haptic cue (backlog)
51 Copy file path (backlog)
52 Maximum file size limit (backlog)
53 Screenshot while recording (backlog)
54 Countdown overlay over other apps: overlay permission (backlog)
55 Notification action to rename or share the last recording (backlog)
- Müzik kapakları: kaydedilen/bulunan kapaklar disk'te (covers), bulunamayan şarkılara iTunes'tan otomatik kapak (haftada bir tekrar), ana iş parçacığında ağ yok. derlenmedi
- Notlar (extras/notes): ana liste, çöp kutusu, kilit (PIN), şifreleme, hatırlatma, widget (küçük/geniş), çizim, ses, fotoğraf, dikte, sesli okuma, bağlantılar, şablonlar, içe/dışa aktarma, istatistik. derlenmedi
- Müzik: Pulse widget seçicisine Music/2x2/4x3/bar kartları; Winamp kabuk kartı yedek önizleme ve pin başarısızlığında uyarı; bildirimde canlı şarkı sözü varsayılan açık. derlenmedi
- Müzik: her Pulse temasına müzik widget'ı renkleri (material/oneui/coloros/aurora/sakura/ocean/ember/frost); şarkı sözü widget'ı (Lyrics) eklendi, seçiciye eklendi. derlenmedi
- Müzik oynatıcı: ses çubuğu kaldırıldı; üç nokta menüsü TrackSheet.kt (kapak bulanık arka plan, Delete onaylı, Playlist, Album Art, Info/Lyrics, Artist/Album/Folder/Genre listeleri). Kırmızı alan albüm kapağıymış, ayrı arka plan değil. derlenmedi
- Müzik: kapak yıldızı favori (uzun bas = puan), kütüphane sıralamasına yıl (yeni→eski), menü alt bilgisine yıl. derlenmedi
- Müzik kapak yıldızı: sadece favori (tıklama), puan seçici (uzun bas) kaldırıldı. derlenmedi
- Ana menü: bildirim geçmişi girişi Tools grubundan kaldırıldı, Ek uygulamalar mağazasındaki anahtarda kaldı (varsayılan kapalı). Müzik yıldızı: dokunma favori, uzun basış puan seçici. derlenmedi
- Launcher: Pulse'un temaya özel duvar kağıtları launcher'da (Win temaları), telefon duvar kağıdı yapma, Win11/Win7 saat ve tarih. Launcher widget'ı yapılmadı. derlenmedi
- Launcher widget desteği: AppWidgetHost, ekleme (bağlama izni + kurulum), home şeridi, ayarlarda ekle/kaldır. derlenmedi
- ProjectHub (yeni): proje tarayıcı + Termux'tan derleme + bildirim ilerlemesi + AMOLED. derle başlatıldı

- Poweramp now-playing: matched Screenshot_20261020_001350_Poweramp.png (mode pills above transport, 84dp play disc, 120dp waveform, folder chip, larger icons). derlenmedi
- Poweramp Now Playing (PowerampScreens.kt): reference 1080x2242 re-measured; blurred cover fills page; sizes set to 2.625 dp/px; derlenmedi
- Poweramp mini bar (PowerampMiniBar.kt, shown above tab pill on list pages while a track is loaded: cover, title, artist, play/pause); right shell button is now a 3-line menu icon, still opens settings. derlenmedi
- Pulse: Sözlerle arama ayarlardan çıkarıldı, Poweramp arama sekmesine 'Sözlerde ara' çipi eklendi (derlenmedi)

## Açık işler (2026-10-20)

- [ ] Poweramp Now Playing: tek tam ekran blur arka plan (iç ve dış katman kalksın), art arka plan kapalıyken siyah (ajan aeae, derlenmedi)
- [ ] Poweramp Now Playing: prev/next/play büyüt, arkasına siyah disk, waveform çizgileri kalınlaştır, referansa göre ölç (derlenmedi)
- [ ] Poweramp Now Playing: alt bilgi chip yerine sanatçı adı (Audio yazmasın)
- [ ] Poweramp kapak: sözler ikonu ile üç nokta yer değiştir (sözler solda)
- [ ] Yıldız: basılı tutma yerine dokununca 5 yıldız açılsın, verilen kadar dolu yıldız gösterilsin
- [ ] Kütüphane: yıldıza göre sıralama seçeneği
- [ ] Kapakta aşağı kaydırma: son açık menü/arama sayfası geri gelsin
- [ ] Alt panel: mini bar + sekmeler tek birleşik panel (yapıldı, derlenmedi)
- [ ] Alt sağ düğme 3 çizgi (yapıldı, derlenmedi); soldaki Kuyruk ikonu için karar bekleniyor
- [ ] Search by lyrics ayarlardan çıkarıldı, Poweramp arama ekranına eklendi (yapıldı, derlenmedi); Material/Winamp/WMP için karar bekleniyor
- [ ] Launcher açılmıyor: neden bulunamadı; logcat (adb logcat -s AndroidRuntime:E) ve varsayılan ana ekran durumu bekleniyor
- [ ] Widget önizlemeleri: 19 widget önizleme görseli eksik, Nova bunları gösteremiyor; yeni görsel üretilecek
- [ ] Widget yüklenmeme: NoiseWidget ve VideoWidget manifest kaydı eklendi (derlenmedi, cihazda doğrulanmalı)
- [ ] Extra apps: ExtraStore, Messages, Launcher ayarları, Ekran kaydı, Bildirim geçmişi hâlâ uygulama içinde açılıyor; ayrı açılması için manifest girişi gerekiyor
- [ ] Extra apps menüsü geri konuldu, Voice/Notes/Weather ayrı açılıyor (derlenmedi)
- [ ] Poweramp Kütüphane, Arama, Efekt, Kuyruk ekranları referansla karşılaştırılmadı
- [ ] ProjectHub 0.1.0: şu an sadece 3 Kotlin dosyası var, tam uygulama mı iskelet mi netleştirilecek
- [ ] Derleme: son değişiklikler derlenmedi; "derle" denince Pulse derlenip latest/ güncellenecek
- [ ] CLAUDE.md "Extra apps" satırı eski, güncellenecek
- [ ] Poweramp Now Playing: tek tam ekran blur (ikinci iç blur kaldırıldı), transport butonları büyük + siyah disk, dalga çubukları kalın, alt chip sanatçı adı (Audio yok) — derlenmedi
- [x] Poweramp: art arka plan artık tüm sekmelerde (kütüphane, arama, efekt, kuyruk), alt panelin katı zemini art arka plan açıkken kaldırıldı (derlenmedi)
- [x] Müzik hub: Material temada sözler açıkken geri tuşu uygulamadan çıkarıyordu; sözler kapanacak şekilde işleyici sırası düzeltildi (derlenmedi)
- [x] Poweramp: üstteki geri başlığı kaldırıldı, sistem geri hareketi kullanılıyor (derlenmedi)
- [x] Poweramp: MusicHub kabuğunun 12/8dp boşluğu ve başlığı Poweramp için kaldırıldı, ekran kenardan kenara (derlenmedi)
- [ ] Poweramp: skin/tema seçimi kaldırılacak, tek sabit Poweramp görünümü kalacak (renkler karışıyor); galeri ve kütüphane ayarlarındaki skin seçici temizlenecek
- MusicHub theme gallery: WMP skins as preview grid, family title thumbnails, Material/Lyrics single preview tile (derlenmedi)
- [ ] Poweramp: one fixed skin (black), skin chooser and skin families removed; Pulse app/src/main/java poweramp files edited - derlenmedi

- Video skin picker: one flat grid of distinct skins (Minimal, Mono, Amber removed as duplicates of AMOLED/Poweramp); family rows gone. derlenmedi
- Extra apps: all standalone apps in the Extra apps card (Voice, Notes, Weather added to the card; Messages, Launcher, Screen recorder, Notification history, Store as new ExtraToolActivity aliases; fresh-install-off list extended) - derlenmedi

- Yeni ekstra uygulamalar (Ses kaydı, Notlar, Hava durumu) kendi görünüm deposu (ToolLook: voice_look / notes_look / weather_look), varsayılan Material; ExtraToolActivity AppLookGuard ile uygulanıp bırakılıyor; Notlar ve Ses ayarlarında görünüm satırı. (derlenmedi)
- Ekran kapağı (Quick Settings kutucuğu CoverTile + CoverService): tüm ekranı kaplayan siyah katman, çift dokunmakla kapanır; saat ve bildirim zili görünür kalır (ayar: Fonksiyonlar, prefs "cover"); zile dokunmak bildirim geçmişini açar; izin yoksa kutucuk izin sayfasını açar - derlenmedi
- Çöp kutusu anahtarı Fonksiyonlar'dan Dosyalar ayarlarına taşındı (Opt.recycle, aynı anahtar; "Çöp kutusunu aç" satırı ile birlikte); Fonksiyonlar'da eski yerine Bildirim geçmişi anahtarı eklendi (NotifHist.enabled/setEnabled, ikon: notifications; Win11'de Fluency PNG) - derlenmedi
- Yapışkan notlar: Windows 7 tarzı renkli not widget'ı (her widget bir not, renk seçimi: sarı, mavi, yeşil, pembe, gri), dokununca StickyNoteActivity düzenleyicisi açılır; kendi görünüm deposu sticky_look (Material varsayılan); ekstra uygulama listesine eklenmedi - derlenmedi
- Kılavuz (features/Guide.kt): Ek uygulamalar, Ekran kapağı, Bildirim geçmişi, Yapışkan notlar, Müzik çalar, Beyaz gürültü, Video oynatıcı bölümleri eklendi; Çöp kutusu ve Ayarlar bölümleri güncellendi. ⋮ menüsünden Kılavuz girişi kaldırıldı (Menus.kt), Ek uygulamalar girişi geri alındı - derlenmedi
- Office ikonu Material/AMOLED'da düz çizgi (mat_office.xml, Ui.kt PIcon); temaya geçiş takılması: widget yenilemesi arka thread'e alındı (MainActivity.setTheme) - derlenmedi
- [x] Winamp: Rebel skinleri (rebel-amp-2-0, rebel-amp-2k) indeksten ve assetlerden kaldırıldı (derlenmedi)
- [derlenmedi] Pulse Widgets: Winamp widgets grouped under a collapsible header (arrow, closed by default, count; search opens it)
- Görev çubuğu (Material/AMOLED): alt çubuk opak (AMOLED saf siyah), sağda saat ve tarih; ana ekranda 'Dok görünsün' anahtarı çubuğu açar/kapatır (mevcut launcher ayarı) - derlenmedi
- [launcher-bg] Kar efekti kodda yok (launcher ve Pulse genelinde snow/flake cizimi bulunmadi). Duvar kagidi secimi Sifirla ile artik sifirlaniyor (LauncherPrefs.kt reset). derlenmedi
- [ ] Pulse widgets: text widget bitmaps capped to ~200k px (RemoteViews 1 MB binder limit, TextTile.fitBinder); Notes pin from picker uses full class name (WidgetsPage addToHome). derlenmedi

## Yeni istekler (2026-10-20)

- [ ] AMOLED temaya şeffaf arka plan seçeneği (opsiyonel), widget şeffaf çalışsın
- [ ] Launcher: tema önizlemesi (preview) ekle
- [ ] Shizuku entegrasyonu (uygulamaya ekle; Functions bölümünde root/Shizuku erişimi zaten var, durumu kontrol et)
- [ ] İnternetten araştırılıp eklenecek ek özellikler (kullanıcı: "extra çok güzel özellikleri varmış, indir ekle")
- [ ] Ek özellikleri uygulamanın çeşitli yerlerine dağıt; ör. terminal kısayolu/bağlantısı
- [ ] App Manager ile birleştirme (uygulama yöneticisi)
- [ ] Files: üstteki dosya yolu kapsayıcısından kopyala/yapıştır
- [ ] Files: üstteki yol kapsayıcısı (path bar) tüm ekranda floating gibi olsun, kaydırmadan görünsün
- [ ] Files: "Tümünü seç" ikonu diğerlerinden ince ve küçük, aynı boyada/stilde olsun
- [x] Müzik ana ekranı: dialer gibi 3x3 öneri ızgarası + aşağıda günlük öneriler, kategori ve enerji/zaman dilimine göre (yerel veriden: en çok dinlenen, son çalınan, kategori)
- [ ] YouTube istemcisi (müzik): araştır, hesap önerileri bölümü. KARAR BEKLİYOR: başka açık kaynak projeden kod kopyalamak lisans gerektirir (çoğu GPL), YouTube hizmet şartları da hesap/özel API kullanımını kısıtlar. Hangi proje ve lisans kabul edilecek?
- [x] Müzik ana ekranı: yerel dinleme verisinden 3x3 öneri ızgarası ve günlük seçimler (Poweramp kütüphane kökü, Material şarkılar sekmesi, WMP kütüphane) (derlenmedi)
- [x] Pulse müzik: kapatınca/durdurunca kendiliğinden çalmama; watchdog yeniden başlatması yalnızca kapanmadan önce çalıyorsa oynatır; bildirim Kapat düğmesi watchdog'u kapatır (0.10.124) (derlendi)
- [derlenmedi] Ekran kapağı: kapak kutucuğu perde ikonu (ic_qs_cover); uzun basış Araç kutusu > Ekran kapağı sayfasını açar (MonitorService.tilePage); Araç kutusunda Ekran kapağı aracı (açma/kapama + ayarlar); ayarlara tarih, pil, kendi yazın eklendi; kapakta yukarıdan aşağı kaydırma kapağı kapatıp hızlı ayarlar panelini açar (root: cmd statusbar expand-settings)
- [derlenmedi] Ana menü: Ana ekran (Launcher) girişi kaldırıldı (Menus.kt); Ek uygulamalar kartında ArtCraft, Ses kaydedici, Notlar, Hava durumu kendi ikonlarını aldı (aynı ikon paylaşımı kalktı)
- [derlenmedi] Launcher boş alan uzun basış: LauncherHome.kt homeLongPress modifier eklendi; LauncherActivity.kt LauncherRoot ana Box'ına .homeLongPress { HomeSheetState.sheet = true } ve içerik sonuna HomeSheet() çağrısı bağlanmalı (sahibi ajan)
- [derlenmedi] Launcher alt sayfa: yeni LauncherHomeSheet.kt (Ayarlar ve Widget'lar simgeli, aktif görünüme göre çizilir; Ayarlar LauncherSettingsActivity açar, Widget'lar seçiciyi açar)
- [derlenmedi] Launcher widget seçici: LauncherWidgets.kt WidgetPreview (gerçek boyutta canlı önizleme, sığdırılır; bağlanamazsa sağlayıcının önizleme resmi) ve WidgetPickDialog (metin satırı yerine önizleme)
- [derlenmedi] Launcher ayarları Widget bölümü: yerleşik widget'lar canlı önizleme olarak; Kaldır yazısı yerine kapat simgesi (LauncherSettings.kt)
- [derlenmedi] Görünüm (Pulse): PTheme.kt bevel() artık 2 px (dış beyaz/siyah, iç açık/koyu gri) - Win95 pencere, düğme, kaydırıcı, görev çubuğu kenarları kalınlaştı
- [derlenmedi] Görünüm (Pulse): MainActivity.kt Windows 7 Aero çerçevesi 4 dp -> 7 dp, XP Luna çerçevesi yan 5 dp kalın mavi kenar; PTheme.kt Win7 diyalog çerçevesi 2 dp, Win10 diyalog kenarı 2 dp
- [derlenmedi] Duvar kağıdı: res/drawable-nodpi/wall_grass.png (çimen, 960x540, prosedürel üretildi) XP, 7 ve 10 listelerinin sonuna eklendi (features/ThemeWall.kt wallsFor)
- [derlenmedi] Duvar kağıdı önizlemesi: yeni features/WallGrid.kt (WallThumb, WallGrid, WallPickDialog) - Pulse Ayarlar'da yatay liste yerine 3 sütunlu önizleme ızgarası (MoreSettings.kt ThemeWallpaperRow); Launcher duvar kağıdı seçici metin listesi yerine aynı önizleme penceresi (LauncherSettings.kt call site, pickWall)
- [derlenmedi] Görünüm: Win11 arka plan A-D paketleri (Backdrop) hâlâ önizlemesiz; ayrıca çimen Win95 listesine eklenmedi (95 dönemi çimen resmi yok)
- [derlenmedi] Ana ekran (Launcher): yuvarlak ekran köşeleri ve yan çentik için kenar boşluğu (LauncherActivity insets, LocalLauncherEdge); alt çubuklar, saat, ayar ve ızgara köşelerde kesilmiyor (tüm görünümler)
- [derlenmedi] Launcher: Windows 95/XP/7/10/11 için başlat menüsü (arama, sabitlenenler, tüm uygulamalar, dışarı dokununca kapanır, görev çubuğunun üstünde); 95/XP/7'de Pulse çekmecesinin üst üste binmesi kaldırıldı
- [derlenmedi] Launcher düzeltmeleri: Win7 arama kutusu çalışıyor, XP sağ sütunu Ayarlar bağlantısı oldu, Win10 başlat paneli sabit boyut yerine ekrana uyuyor, Material alt çubuğu köşeden uzak
- [derlenmedi] Poweramp: dalga 36 sn'lik kayan pencere ve gerçek ses zarfı (TrackPeaks), sürükleyerek arama; play düğmesi büyütüldü (116x52). Sanat arka planı kapalıyken alt panel ve mini bar plakası kaldırıldı; menü ile Poweramp tek 'artBg' ayarını paylaşıyor; menüde açıkken yarı saydam katman. Kütüphane: öneriler en alta, 'Tüm şarkılar', 'Tüm sesler' ve 'Daha fazla kategori' kaldırıldı. Kapağı olmayan şarkılar kütüphane yüklenince arka planda indiriliyor (CoverFetch.backfill, 3 sn aralık, internet varken). Launcher: uzun basış sayfası (HomeSheet) bağlandı.
- [derlenmedi] Poweramp kapak: tek jest yöneticisi (yatay kaydırma kapağı taşır, yukarı = lyrics, aşağı = son açık sayfa). Lyrics paneli aşağıdan kayarak açılır, aşağı sürükleyince kapanır. Lyrics düğmesi yalnızca sözü olan şarkıda görünür; ayarda 'her zaman göster' anahtarı var. Sanat arka planı sayfa bazında (Şimdi çalıyor, Kütüphane, Arama, Efektler, Kuyruk) açılıp kapanır, her sayfa için önizleme var.
- [derlenmedi] Cihaz sorun giderme: ⋮ menüsünden kaldırıldı (Menus.kt), Ayarlar > Hakkında'ya düğme olarak eklendi (About.kt).
- [derlenmedi] Ek uygulamalar kartı: 'Ana ekran' satırı yalnızca Launcher anahtarı açıkken görünüyor (MoreSettings.kt).
- [derlenmedi] Windows 11 materyali: Ayarlar'daki Mica / Mica Alt / Acrylic / Glass seçimi launcher'ın görev çubuğu ve başlat menüsüne bağlandı (Win11LauncherLook.kt); 'Blur' seçeneği kaldırıldı (Mica.kt, Pages.kt), kayıtlı 'blur' Mica'ya düşüyor (MainActivity.kt).
- [derlenmedi] Windows 11 materyali: 'Blue' (bulanık duvar kağıdı + mavi ton) ve 'Liquid glass' (üst kenarı parlak, mercek kenarlı açık panel) eklendi (Mica.kt); Win11'de seçilen materyal artık kenar panelinin cam görünümünü ezer (Mica.kt layer); açıklama metni güncellendi (Pages.kt).
- [derlenmedi] Widget Liquid glass: yeni widgets/glass/LiquidGlass.kt (duvar kağıdı arka planı, bulanık, kenarda AGSL mercek (Android 13+; altta yalnızca bulanıklık + parlak kenar), cache'li). Win11Material.kt: material "liquid" dalı eklendi.
- [derlenmedi] ThemeWidgets.kt: Glass ve iOS widget'larında material "liquid" ise liquidTile() ile liquid tile çizilir, skin'in kendi tile dolgusu atlanır (draw ve liveDial).
- [derlenmedi] Widget Liquid glass seçimi: widget seçici/ayar penceresinde (WidgetConfig.kt, WidgetsPage.kt) 'Liquid' materyal çipi henüz yok; bugün yalnızca WidgetOver mat_<id>="liquid" ile etkin.
- [derlenmedi] Windows 11 cam paneller: sabit bulanık tüm-uygulama resmi kaldırıldı (MainActivity.kt Root/FilesRoot); duvar kağıdı GlassRoot ile pencerenin arkasında, material 'none' değilse pencere saydam. Stok duvar kağıdı seçili resim yokken kullanılır (Backdrop.kt stock).
- [derlenmedi] Backdrop.kt: stock (splash_win11_wallpaper) ayrıca çözülüyor; Root sabit blur katmanı çizmiyor, veri aynı kaldı.
- [derlenmedi] GlassKit.kt: inGlassRoot() eklendi (GlassRoot kapsamı, ilk karede de doğru).
- [derlenmedi] Mica.kt: Mica, Mica Alt, Acrylic, Glass, Blue, Liquid glass için Modifier.glassMaterial (GlassKit glass + grain/üst parlaklık); backdrop()/layer() GlassRoot içinde glass'a yönleniyor, dışında eski yol.
- [derlenmedi] Ui.kt PCard: Windows 11 materyali seçiliyken kartlar gerçek cam (glassMaterial).
- [derlenmedi] Pages.kt: Windows 11 materyal açıklaması cam ve 'arka plan duvar kağıdı' olacak şekilde güncellendi.
- [derlenmedi] iOS / Glass widget'ları: sıvı cam (liquid) artık varsayılan; widget'ın kendi malzemesi kayıtlıysa o geçerli (WidgetPrefs.materialSet, ThemeWidgets.liquidTile). Widget ayarlarına Liquid glass chip'i eklendi (WidgetConfig.kt).
- [derlenmedi] Winamp widget'ları 'Can't load widget': bitmap RemoteViews sınırını (~1 MB) aşıyordu; TextTile.fitBinder ile 250k px'e indirildi (WinampWidget.kt).
- [derlenmedi] Dosyalar: ızgarada sütun sayısı ikon boyutundan hesaplanıyor (boşluk büyümüyor); dosya önizlemesi ve tür ikonu yuvarlak köşeli kare (%22); seçim çubuğundaki tüm butonlar aynı 40dp kare, 24dp simge (Explorer.kt).
- [derlenmedi] Dosyalar boyut ayarı: basılı tutunca çıkan çizgi yerine tüm yol kartı boyut çubuğu oluyor; dolu kısım ve kenar tutamacı (Explorer.kt sizeOverlay).
- [derlenmedi] Arşiv içinde (zip, apk, jar, tar, tar.gz): dosyalar tek tek seçilebiliyor (basılı tut / dokun), seçim çubuğundan kopyala (panoya al, sonra başka klasörde yapıştır) veya bulunduğu klasöre çıkar (Explorer.kt arcSel, ExplorerArchive.kt archiveExtractSel). 7z ve RAR hâlâ desteklenmiyor.
- [derlenmedi] Arşiv (zip): toplu seçim (tümünü seç), kes = klasöre çıkar + arşivden sil (ExplorerArchive.kt archiveRemove, arcCut). APK'te kesme engelli. 7z/RAR yok.
- [derlenmedi] Dosyalar: çekmecede Kütüphane bölümü (standart klasörler + Ekran görüntüleri), ayarlardan her biri açılıp kapanıyor; 'Veri (/data)' satırı; root satırları artık sonradan algılanan root için de görünüyor (Explorer.kt, ExplorerArchive.kt).
- [derlenmedi] Dosyalar: AMOLED seçilince Material tabanı + Pulse teması karışıyordu; artık Dosyalar AMOLED'i kendi amoledWindow() temasıyla çiziyor (MainActivity.kt LookHost).
- [derlenmedi] Pulse ayarları: 'Açılış animasyonu' anahtarı eklendi (Opt.splash, MainActivity açılış koşulu, MoreSettings yanındaki giriş ayarı yanında).
- [derlenmedi] Windows 11 ayarlar ikonları: 'note' ve 'recycle' anahtarları Fluent tablosunda yoktu ve 'info' simgesine düşüyordu; mevcut 'notes' ve 'trash' çizimlerine takma ad olarak bağlandı (FluentPaths.kt).
- [derlenmedi] Windows 11 ikonları: Fluency PNG paketi kaldırıldı, Windows 10 ile aynı ince çizgi vektörleri (FluentPaths) kullanılıyor; çöp kutusu BinIcon (Ui.kt PIcon).
- [derlenmedi] Windows 11: ikonlar varsayılan Fluent; Ayarlar > Görünüm'de 'Çizgi' seçilince Windows 10 tarzı çizgi (Look.winIconLine). Ekstra uygulamalar Windows 11 + malzeme açıkken cam kökle (wallpaper arkada) çiziliyor (ExtraTool.kt).
- [derlenmedi] Araçların yeri: Office, İndirme yöneticisi, Uygulama kilidi, Sağlık, APK yükleyici, Cihaz sorun giderme, Kimlik doğrulayıcı, Ağ kullanımı için Menü / Ek uygulamalar / Yalnız ayarlar seçimi (Menus.kt ToolPlace, MoreSettings.kt).
- [derlenmedi] Araçlar: sol menüye eklenebilir (Settings > Araçların yeri > Sol menü), Ek uygulamalar kartında her araç açılabilir (sayfa tabanlılar ana uygulamada açılır); Office ve İndirme yöneticisi için siyah zeminli beyaz çizgi ek uygulama ikonları (ic_tile_office, ic_tile_download).
- [derlenmedi] Ek uygulamalar kartı: Windows 11'de her uygulamanın kendi Fluent ikonu (FluentPaths: extraFiles…extraStore, 16 yeni anahtar; MoreSettings.kt ExtraAppsCard, diğer görünümler eski tile).
- [derlenmedi] App manager: Extract APK sırasında 'Çıkarılıyor…' gösteriliyor, sonuç yazısı beyaz; çıkarma klasörü Ayarlar'dan seçiliyor (varsayılan Download/Pulse/extract); arama kutusunda X ile temizleme.
- [derlenmedi] Ek uygulama kısayolları varsayılan kapalı (manifest, 16 alias enabled=false); Mağaza ek uygulamalardan kaldırıldı; Office ve İndirme yöneticisi varsayılan Ek uygulamalar; kutu ikonları karanlıkta vurgu rengi, açıkta siyah.
- [derlenmedi] Derleme hataları düzeltildi: ExtraTool stock null, LauncherWidgets matchParentSize importu, PowerampScreens virgül ve Long dönüşümü, AppDetails tr importu, CoverTile PField importu.

## 2026-10-20 — Win11 geri alma + QS perde ikonu (derlenmedi)
- Mavi ve liquid materyalleri Effects listesi ve Mica.kt/Win11Material/ThemeWidgets/WidgetConfig/Pages/MainActivity dosyalarından çıkarıldı; "Blur" geri geldi, eski blur kaydırıcıları ve açıklama metni geri yazıldı.
- iOS/Glass liquid widget (widgets/glass/LiquidGlass.kt) silindi; saat ve pil kartlarının arka planı eski haline döndü.
- ic_qs_cover.xml: perde ikonu büyütüldü (tüm 24 birim alanı kaplıyor).
- Bekleyen: liquid glass kendi teması, Office/İndirme Yöneticisi extra app olarak (toggle + launcher ikonu), düz Win11 ikon tuşu ile Office içi yeni düz ikon.
- Pulse Poweramp kapak ekranı: başlık 20->26sp, alt satır 16->20sp, çipler tam genişlik ve daha geniş iç boşluk (PowerampScreens.kt ~satır 311-321). derlenmedi
- Pulse Poweramp sanatçı/albüm satırına uzun basma: seçilebilir işlem (yok, şarkı sözleri, ekolayzır, uyku zamanlayıcı), ayar Poweramp bölümünde radyo listesi; PowerampLineAction.kt yeni, PlayerSettings.kt ve PowerampScreens.kt düzenlendi. derlenmedi
- Pulse müzik: kapakta yatay kaydırma ayarı (CoverGestures.kt, TrackArt.kt CoverCarousel, PlayerSettings.kt Playback) ve Bluetooth ses cihazı bağlanınca çalma (BluetoothAutoplay.kt, MusicPlaybackService.kt receiver, BLUETOOTH_CONNECT manifest, PlayerSettings.kt satırı). derlenmedi

## 2026-10-20 — Dosya gezgini: sıkıştırma arka planda, boşluk, seçim köşesi, kes/kopyala bildirimi (derlenmedi)
- Sıkıştır: artık hemen çalışmıyor; kutu açılıyor (ad, tür, öğe sayısı, boyut, tahmini zip boyutu), onaylanınca ZipService içinde arka planda çalışır, bildirimde ilerleme ve İptal, bitince ayrı bildirim; ekran kapansa da devam eder (features/ExplorerZip.kt, AndroidManifest ZipService).
- Izgara: dosyalar arası boşluk büyük ikonlarda açılmıyor; sabit 6dp boşluk, artan genişlik kenarlara gidiyor (Explorer.kt grid bloğu).
- Seçim çerçevesi: liste, ızgara ve arşiv satırlarında yarım daire yerine 16dp köşe (SEL_SHAPE).
- Kes/kopyala düğmeleri artık bildirim (not) gösteriyor; neden çalışmadığı kodda bulunamadı, cihazda tekrar bakılmalı.

## 2026-10-20 — Poweramp çalma ekranı: sanatçı, Sanatçı/Albüm/Klasör/Tür menüsü, play diski, dalga (derlenmedi)
- Sanatçı: etiket boşsa dosya adındaki "Sanatçı - Başlık" kısmı gösteriliyor (PowerampStyle.kt artistTag).
- Sanatçı/Albüm/Klasör/Tür menüsü: oynatıcının parçası kütüphane kopyasıyla eşleniyor (tür ve yıl gelsin diye, PowerampApp.kt inLibrary).
- Play/pause siyah disk 116dp -> 84dp, ikon 52 -> 40dp.
- Dalga çubukları: yükseklik 142.5 -> 64dp, 46 -> 80 ince çubuk, pencere 36 sn -> 20 sn, oynatma imleci 250 ms -> 120 ms yenileme.

## 2026-10-20 — Poweramp dalga -> ses çubukları, Winamp widget yedek çizim (derlenmedi)
- Poweramp: dalga formu (sabit peak) kaldırıldı; çubuklar MusicLevels canlı bantlarıyla (24 band) hareket ediyor, SpectrumBars.kt. TrackPeaks.kt silindi (kullanılmıyor).
- MusicLevels: yükselme .7 -> .92, düşüş .88 -> .8 (daha hızlı tepki). Winamp görselleştirici de bundan etkilenir.
- Winamp widget: çizim hata verirse varsayılan skinle çiziliyor (WinampWidget.build -> buildFull), launcher "widget yüklenemedi" demesin.
- Açık: "Winamp widget altı çalışmıyor" hangi kısım belirsiz; skin kartındaki ekle ikonu launcher pin desteklemezse çalışmıyor.

## 2026-10-20 — Albüm kapağı kaydırma: yan kaydırmada eski kapak kaybolmuyor, dikey çekme yay gibi dönüyor (derlenmedi)
- TrackArt.kt CoverCarousel: kaydırma bitince yeni parçanın kapağı gelene kadar kayarak giren kapak ekranda kalıyor (eski kapak geri zıplamıyor).
- Dikey çekme kapağı hafifçe takip ediyor (en fazla yarım eşik) ve yay gibi geri dönüyor; yukarı/aşağı eşiği eskisi gibi.

## 2026-10-20 — Eksik albüm kapağını internetten indirme ayarı (varsayılan açık) (derlenmedi)
- Müzik Ayarları > Çalma: "Eksik albüm kapaklarını internetten indir" anahtarı (MusicSettings.onlineCovers, varsayılan açık).
- Kapalıyken CoverFetch.backfill ve parça başına arama yapılmıyor; açılınca eksik kapaklar hemen taranıyor.

## 2026-10-20 — Başlat menüsü: uygulamalar kategoriye göre gruplu (Windows 10 ve 11) (derlenmedi)
- LaunchApp.category (ApplicationInfo.category); startGroup/groupedApps (LauncherCore.kt): Oyunlar, Medya, İletişim, Üretkenlik, Harita, Sistem, Diğer.
- Win10 listesi ve Win11 ızgarası gruplara ayrıldı; arama yapılınca düz liste.
- Açık: arkaplan önizlemesi, Fluent ikon seti, saat/tepsi, masaüstü ikon yerleşimi henüz değişmedi.

## 2026-10-20 — Windows Phone başlatıcı görünümü + AMOLED varyantı (derlenmedi)
- WinPhoneLauncherLook.kt: görev çubuğu yok, sol kenarda dock (≡ uygulamalar düğmesi + seçili uygulamalar), ana ekran renkli kutular, uygulamalar ekranı harf sıralı liste.
- LauncherLook.SideDock (varsayılan boş) ve LauncherActivity satırı: Row + SideDock + Column.
- Açık: web/Play Store araştırması yapılmadı; Windows 10 masaüstü görünümü değişmedi; yazı tipi sistem fontu.

## 2026-10-20 — Pulse başlatıcısı uygulamadan kaldırıldı (kod duruyor) (derlenmedi)
- AndroidManifest: LauncherActivity artık HOME ve LAUNCHER girişleri olmadan, exported=false (uygulama çekmecesinde ve ana ekran seçeneğinde yok).
- MoreSettings: Ek uygulamalar kartındaki "Ana ekran / Launcher" satırı kaldırıldı.
- Kod extras/launcher içinde duruyor; tamamen silmek istenirse ayrıca yapılır.
- [ ] Windows Phone look (winphone + winphone_amoled) redesigned: live tiles scaled to width, apps pivot with A-Z row, start glyph dock. derlenmedi
- Launcher Material You ailesi (MaterialLauncherLook: AMOLED, One UI, ColorOS, Aurora, Sakura, Ocean, Ember, Frost) yeniden tasarlandı: yüzen yuvarlak dock, pill arama, tonlu grup kartları ile çekmece. Derlenmedi (Gradle çalıştırılmadı).

## 2026-10-20 — Win7 launcher look redesign (derlenmedi)
- Win7LauncherLook.kt: gradient taskbar with glossy drawn orb, show-desktop strip, Start menu with folder group rows and search box at the foot, light split Shut down button, desktop labels with shadow. Public class, id and constructor unchanged.
- [ ] Win10 look redesign (Win10LauncherLook.kt): taskbar 40dp with 46dp buttons, start rail, tile groups, search rows, drawn icons; black variant class param (win10black) for registry; derlenmedi
- [ ] Win11 look redesign (Win11LauncherLook.kt): centred Fluent taskbar, start flyout with pinned / recommended / all-apps pages, white desktop labels with shadow. Not compiled (derlenmedi).
- XP look redesigned (XpLauncherLook.kt: Luna taskbar, green Start button, two-column Start menu, tray, icon labels). derlenmedi
- 2026-10-20 — Win95 başlatıcı yeniden çizildi (Win95LauncherLook.kt): 2 px çerçeveler, Başlat düğmesi ve bayrak, tepsi saati, Başlat menüsü dikey bannerı, Bul penceresi (lacivert başlık çubuğu), klasörlü program listesi (derlenmedi)
- [poweramp-wave-revert] Müzik çalar dalga: canlı SpectrumBars kaldırıldı, WAVE stili 0.10.117 hâlindeki sabit (tohumlu) SeekBar'a döndü; şarkı/sanatçı kutuları yazı genişliğinde ve daha koyu (Color.Black .6). Ana ekran: LauncherActivity HOME/DEFAULT filtresi geri eklendi (varsayılan ana ekran listesinde görünsün, uygulama çekmecesinde değil). derlenmedi
- [files-ui] Dosyalar: üst çubuktaki +/- zoom tuşları kaldırıldı (boyut slider'ı yapıyor); üst ikonlar 20→23 dp; seçim çubuğu düğmeleri 34→30 dp, ikonlar 20→17 dp; ızgarada aralık 4 dp sabit, hücreler genişliği eşit paylaşıyor. [zip-type] Sıkıştır kutusunda ZIP/7Z seçimi (ZipJob.kind, SevenZOutputFile ile 7z yazımı). [archive-paste] Arşiv içinde yapıştır (zip; archiveAddZip). Açık: 7z/rar içinde kes/yapıştır, rar yazılamıyor. derlenmedi
- [archive-cut] Arşivde Kes artık panoya taşıma olarak koyuyor (filesDir/arcclip, arşivden silinir), üst çubukta yapıştır çıkıyor; yapıştır standalone Dosyalar uygulamasında da görünüyor. Zip dışı arşivlerde kes hâlâ yok. derlenmedi
- [messages-copy] Mesajlar: iş parçacığı başlığında kişi/numaraya basılı tutunca numara kopyalanır. Ek dosya (MMS) bekliyor: PDU kodlayıcı gerekiyor. derlenmedi
- [messages-attach] Mesajlar: konuşma satırına uzun basınca menü (Sabitle, Arşivle, Engelle), Arşivlenenler ayarlarda geri alınır. Ek dosya: Ekle düğmesi, dosya MMS olarak gider (com.klinkerapps:android-smsmms 5.2.6, okhttp dışlandı, sistem MMS yığını). MMS gönderilen mesajlar konuşmada henüz görünmüyor (yalnız SMS okunuyor). derlenmedi
- [win11-layer] Windows 11 görüntülü modunda açık mod katmanları koyulaştırıldı (Mica.kt overPicture, açık mod alfa .42-.6, gri tonları koyu). derlenmedi
- [open] Ek uygulama açılınca tema karışması: kök neden doğrulanmadı. Look.resolved düz var (state değil), MainActivity.onResume geri yüklüyor ama Pulse yeniden composition yapmıyor olabilir. Mesajlar/Ekran kaydı dış açılışı: ScreenRecActivity ayrı görev olarak açılıyor olabilir, doğrulanmadı. Notlar 'paint gibi' hedefi belirsiz (ArtCraft mı, Windows Paint mi?).
- [notes-paint] Notlar çizim: araç çubuğu Paint tarzı, sabit renkler (tema bağımsız): 16 renk, 3 kalınlık, geri al/sil/onay. Kalem rengi/kalınlığı değişince doğru anahtarla çizim güncellenir. derlenmedi
- [weather-visuals] Hava durumu: güneş yayı (gündoğumu-batımı, güneşin konumu) ve ay evresi çizimi (aydınlık %, döngü günü) eklendi, WeatherVisuals.kt; WeatherFmt.moonAge/SYNODIC. derlenmedi
- [files-data] Dosyalar: sol menüde en üstteki Veri (/data) girişi kaldırıldı; /data/data listeleme Shizuku'da kök gerektirdiğini gösteriyor. AMOLED'de durum çubuğu siyah (paintWindow). derlenmedi
- [shizuku-ask] Shizuku: binder geldiğinde ve izin yoksa, süreç başına bir kez requestPermission (Shizuku yöneticisi Pulse'u listelesin diye). Sampler.kt. derlenmedi
- [contact-info] Manifest: ContactsActivity'e content://com.android.contacts VIEW filtresi (contact, raw_contact). [callbg] extras/callbg/SamsungCallBg.kt: root ile contacts2.db'den call_bg yolu, güvenli yol kontrolü, önbelleğe kopya, önizleme. Şema cihazda doğrulanmadı. derlenmedi
- [weather-preview] Widget seçici: düz Hava durumu 110x110 kare, geniş 250x110 gerçek oranda çiziliyor (WidgetsPage TextCard). derlenmedi
- [widget-load] PulseWidget.onUpdate hata olursa widget_fallback (Pulse yazılı) gösterilir, log etiketi PulseWidget. Music widget'ları kendi onUpdate'lerinde bu korumaya sahip değil. Kök neden doğrulanmadı. derlenmedi
- [winamp-default] WinampWidget: skin tanımsızsa DEFAULT_SKIN=base-2.91.wsz kullanılır; dosya assets/skins'e + index.json'a eklenmeli (indirilemedi). derlenmedi
- [docscan-gallery] Belge tara: Galeriden düğmesi (GetContent image/*), seçilen görsel kopyalanıp aynı sayfa işleme akışına girer; kamera yoksa da çalışır. derlenmedi
- [extra-tools-fold] Ayarlar > Ek uygulamalar kartının altında 'Araçların yeri': her araç daraltılmış satır (ikon, ad, mevcut yer, ok); açılınca Menü / Ek uygulamalar / Sol menü / Yalnız ayarlar çipleri. Eski düz 'Araçların yeri' bloğu Functions ayarlarından kaldırıldı (MoreSettings.kt ExtraAppsCard, FunctionsSettings). derlenmedi
- [extra-tools-switch] Ek uygulamalar kartında araçlar (Office, İndirme yöneticisi, Uygulama kilidi, Sağlık, APK yükleyici, Kimlik doğrulayıcı, Ağ kullanımı, Ekran kapağı, Araç kutusu) tek listede: Windows 11'de Fluent vektör ikon (FluentGlyph, PNG paketi atlanıyor), anahtar (açık = Ek uygulamalar kartında, kapalı = yalnız ayarlar), ok ile daraltılınca yer çipleri. Eski üst araç satırları kaldırıldı (MoreSettings.kt ExtraAppsCard). derlenmedi
- [crash-fix] Açılış çökmesi: MainActivity açılırken 'Component class HomeLauncher does not exist' (MoreSettings.kt listesinden HomeLauncher çıkarıldı, her setComponentEnabledSetting runCatching içine alındı). Ayrıca 'MutableState(value=[]) cannot be saved' (MainActivity.kt App: trail rememberSaveable -> remember). derlenmedi
- [crash-fix] TaskMgr.kt: expanded/collapsed setOf rememberSaveable -> remember (aynı kaydetme çökmesi). derlenmedi
- [compile-fix] MoreSettings.kt FluentGlyph: 'Unresolved reference scale' (0.10.135 derleme hatası) -> scale/Canvas/Offset/PathParser import edildi. derlenmedi
- [shizuku-noauto] Sampler.kt watchShizuku: açılışta otomatik requestPermission kaldırıldı (açılışta izin penceresi çıkmasın). İzin isteği yalnız Shizuku düğmesinden gider. Shizuku Next paket adı doğrulanamadı; detection listesine eklenmedi. derlenmedi
- [linkprops-verify] VerifyError 'w4.Dd (Sampler$Procs).e2 cannot access b.c.q (android.net.LinkProperties)': android-smsmms 5.2.6 bundles android.net.LinkProperties/LinkAddress/RouteInfo/ProxyProperties; R8 renamed them and inlined mIfaceName into Hw.network. app/proguard-rules.pro: -keep android.net.* (5 classes). Kural tutarlılığı derleme ile doğrulanmadı; hâlâ çökerse mapping.txt'te android.net.LinkProperties satırına bak. derlenmedi
- [shizuku-welcome] Shizuku izni yalnız ilk açılış karşılama ekranının Başla düğmesinde sorulur (Welcome.kt finish): Shizuku çalışıyor (shizuku==1) ve root yoksa askShizuku. Sonraki açılışlarda istek yok. Shizuku kaldırılmadı (root yoksa yedek yöntem). derlenmedi
- [derle-ui] derle_pulse.sh: 3 adım (sürüm, Gradle bul, derle), renkli kutu, dönen gösterge, geçen süre ve aktif Gradle görevi; Gradle yolu bulunamazsa net hata. build_one.sh: GRADLE_BIN (önce /root/tools/gradle-8.9, sonra PATH), kilit $HOME altında. derlenmedi

## 2026-10-21 — kullanıcı listesi (yapılmadı, sırada)
- [music-album-dl] Müzik çalar albüm indirmeye çalışıyor ama indirme yapılmıyor.
- [music-play] Hiçbir müzik açılmıyor; oynatıcı çalışmıyor. (Yapılmadı, sadece not.)
- [treesize-root] TreeSize ve Disk Usage: kök dizini (root kısmını) görebilsin.
- [image-zoom] TreeSize ve Disk Usage: bir resme basınca büyütülsün (önizleme).
- [treesize-view] TreeSize: görünüm tercihi eklensin; Windows 11 tarzı kare kutucuk görünümü gibi, seçilebilir.
- [office-menu] Excel ve PowerPoint üst menüsü Word gibi olsun; şu an bozuk.
- [archive-paste] Sıkıştırılmış dosyalarda (zip vb.) Kes çalışıyor ama Yapıştır çıkmıyor; yapıştır görünsün.
- [settings-icons] Ayarlarda ikonlar: Windows 11 varsayılanı Fluent olsun (şu an çizgi); her biri için collapsed (daraltılmış) seçeneği eklensin ve her birini ayrı seçilebilsin.
- [contacts-default] Contacts varsayılan yapılamıyor; düzeltilsin.
- [main-dns-buttons] Ana uygulamada DNS için görünen varsayılan düğmeler kaldırılsın.
- [winamp-widget] Winamp widget eklendi ama widget ekranında gösterilmiyor; düzeltilsin.
- [widget-preview-fix] 16 widget XML'lerinde android:previewImage eklenmişti ama karşılık gelen preview_widget_* çizimleri yok (aapt hatası olurdu). Bu eklemeler geri alındı; xml klasörü HEAD ile aynı. Gerçek önizlemeler (widget boyutunda, tam pencere) ayrıca üretilecek. derlenmedi
- [widget-view] 'Can't display widget' (Winamp, Winamp 4x1, Poweramp): <View> is not allowed in RemoteViews layouts; widget_winamp, widget_winamp_compact, widget_music_winamp, widget_poweramp now use ImageView (click zones and spacers only). Kok neden: RemoteViews inflate hatasi. derlenmedi
- [music-anim] Material oynatici: now-playing ekrani asagidan kayarak acilir/kapanir (AnimatedVisibility), kapak calarken nefes alir / durunca kucuk (graphicsLayer scale), oynat/duraklat ikonu Crossfade, kapakta yukari kaydirma kuyrugu acar ve asagi kaydirma kapatir (Poweramp gibi), calan sarkida animasyonlu cubuklar (PlayingBars). Tasarim degismedi. derlenmedi
- [winamp-default] Winamp widget'lari (4x2 ve 4x1): yerlestirirken skin secici acilmaz, varsayilan skin (classic-ebony) direkt kullanilir (configuration_optional; sonradan yeniden ayarlanabilir). Widget secicide varsayilan skinin gercek penceresinden uretilen onizleme (preview_widget_winamp, preview_widget_winamp_compact). [cover-fast] Kapak: once yerel/gomulu kapak aninda gosterilir, internet aramasi ikinci adim (TrackArt.kt). [files-rooterr] Dosyalar: root listesi bos donerse nedeni 'Dosya yok' yerine ekranda yazar. derlenmedi
- [extra-menu] Ust menu (3 nokta): 'Extra apps' bolumu acilir/kapanir, icinde Ek uygulamalar kartindaki araclar (Indirme yoneticisi, Office, Uygulama kilidi, Saglik, APK yukleyici, Kimlik dogrulayici, Ag kullanimi, Ekran kapagi, ...) ve Magaza; Tools grubunda yalniz menuye konanlar kalir (Menus.kt ToolPlace.extraEntries). [fluent-real] FluentPaths: microsoft/fluentui-system-icons (MIT) gercek SVG yollari indirildi (tools/fetch_icons.py yerine scratch betigi): tool_* (her arac ayri ikon) ve extra* ikonlari; Windows 11 Ek uygulamalar kartinda her satir 52 dp, ikon 36 dp yuvada. Acik: uygulama satirlarina araclardaki gibi 'nerede gorunur' katlanir bolumu eklenmedi. derlenmedi
- [apps-card] Ayarlar: 'Pages shown' karti kaldirildi; 'Extra apps' karti 'Apps' oldu (ust menu grubu da 'Apps'), sayfalar (sol menu) da bu kartta anahtarlarla; araclarin 'nerede gorunur' secimi ikon + radyo (menu, Apps, sol menu, gizli). Acik: uygulama (Dosyalar vb.) satirlarinda yerlesim secimi yok. [files-size] Dosyalar: satir araligi azaldi, secim hapi kalin (4 dp), buyutunce yazi da buyur (ts/tsg). [artist] Track.shownArtist(): etiket yoksa 'Sanatci - Baslik', klasor adi, yoksa 'Bilinmeyen sanatci'; mini cubuk, widget'lar, WMP, Material'da kullanilir. [wave-window] Poweramp dalga cubugu: sarkinin ~30 sn'lik kismi gorunur, calma konumu ortada sabit, dalga sarkiyla akar, surukleyince ilerler, dokununca atlar (SeekBar windowFrac). derlenmedi
- [cover-wheel] Kapak (CoverCarousel): kapaklar uzak bir cemberin uzerinde, yana kayinca ~20 derece yayla sallanir; yukari/asagi surukleme parmagi izler, kare disina cikar, esigi gecince kapak tum sayfayi asip gider ve onceki ekran acilir (Poweramp: yukari = sozler, asagi = geri; Material: ikisi de kapatir). Kapak kosesi artik kare (4 dp). [search] Poweramp aramasi: ek dugme yok, yazdikca sarki/sanatci/albumde arar, 3+ harfte sozlerde de arar; sonuclar Sarkilar, Albumler, Sozler (esleşen satirin ustu ve alti birer satir). [bottom-label] Now-playing alt cip: varsayilan ses kalitesi (AudioQuality: format, kHz, bit, kbps); Ayarlar > Poweramp > 'Tuslarin altindaki yazi' (ikon, aciklama, onizleme, radyo): kalite, sanatci, albüm, sarki adi, klasor. [suggestions] Now-playing sayfasinin altina oneriler (RecommendationHome) geri eklendi. Acik: Material oynaticida arama hala yalniz ad/klasor. derlenmedi (build bekletiliyor)
- [readme] README guncellendi (Android 12+, Apps karti, Office, Launcher, Dosyalar, mesaj/notlar/hava, muzik oynatici yenilikleri). REDDIT_POST.md eklendi (baslik + govde, kopyalanip paylasilacak). [launcher-desktop] Ana ekran masaustu: Windows looklarinda Bu bilgisayar, Geri Donusum Kutusu, Dosya Gezgini, Paint, Komut Istemi kisayollari (ikonlari looka gore); diger looklarda temiz ana ekran (saat, widget, dock); uygulama izgarasi ayardan geri acilir (homeApps). Uygulama listesi kategorili (mevcut StartGroup). [music-home] Poweramp kutuphane: etiketsiz sarkilar da Albumler/Sanatcilar/Turler gruplarina girer (klasor / Bilinmeyen), Tum sarkilar koke eklendi, her sarki listesinde Cal ve Karistir ikonlari, dokunulan sarki o listeyi calar; MediaStore IS_MUSIC filtresi gevsetildi. derlenmedi
- [stars] Yildiz verilen sarkilar: Favoriler listesi artik favoriler + yildizlilar (en yuksek puan once); Istatistikler'e 'Yildizli sarkilar' bolumu. [online-tags] Internetten etiket indirme (OnlineTags, iTunes arama, anahtarsiz): etiket duzenleme penceresinde 'Internetten bul' (sanatci, album, tur, yil) ve tur/yil alanlari; Ayarlar > Calma > 'Eksik etiketleri indir' acikken arka planda eksik album/tur/yili doldurur (varsayilan kapali, 3 sn arayla, basarisiz aramayi 1 hafta tekrarlamaz). [launcher-taskmgr] Launcher: masaustu simgeleri gercek masaustu gibi sol kenardan asagi akip yeni sutuna gecer; Gorev Yoneticisi kisayolu (Windows looklarinda masaustunde, digerlerinde ust sag ikon; Pulse Islemler sayfasini acar). derlenmedi
- [license] LICENSE eklendi: GNU GPL v3.0 (UAD listesi GPL-3.0 oldugu icin uyumlu). README ve REDDIT_POST guncellendi. [m3u] .m3u/.m3u8 calma listesi dosyalari bulunur (MediaStore + Music/Playlists/Download/Documents/Pulse, 3 seviye), Calma listeleri sayfasinda kayitli listelerle birlikte; liste veya kare (kapakli) gorunum dugmesi. [mix] Oneriler artik hic dinleme yokken de dolu: en cok sarkisi olan sanatcilar + ruh haline gore kisisel mixler (Enerjik, Sakin, Odak, Nese, Gunluk; tur etiketi ve basliga gore, calma/yildiz/favoriye gore sirali, gunluk degisir, saate gore siralanir). derlenmedi
- [paint] Yeni ek uygulama 'Paint' (MS Paint tarzi): PaintLauncher alias (kapali baslar), route 'paint' (ExtraToolActivity, kendi look deposu PaintLook), PaintHub: kalem, firca, silgi, boya kovasi, damlalik, cizgi, dikdortgen, elips; 28 klasik renk; kalinlik; geri al (10); resim ac; Pictures/Paint'e PNG kaydet. Masaustu kisayolu Paint artik bunu acar. ArtCraft aslinda cizim degil APK indirici (README duzeltildi). [online-pl] Online playlist: Piped yanit vermezse Invidious yedek kaynak (playlist, arama, ses akisi); zaman asimi 5 sn. Sandboxtan test edilemedi. [cover-fix] Kapak karisikligi: internet aramasi artik ayni albume / sarkiya ve ayni sanatciya eslesmeyen sonucu kaydetmez; Ayarlar'da 'Kaydedilen kapaklari temizle'. [blank-pages] Yil/besteci yoksa 'Yil yok' / 'Besteci yok' grubu; bos liste 'Burada sarki yok' yazar. [windowed] Launcher: 'Uygulamalari pencerede ac' (Windows looklari, serbest pencere moduna ihtiyac var). derlenmedi
- [paint-window] Paint: Windows looklarinda gercek Paint penceresi. 95/XP: baslik cubugu, menu cubugu (Dosya Duzen Gorunum Resim Renkler Yardim), sol arac kutusu (2 sutun + cizgi kalinligi), alt renk kutusu, durum cubugu ('For Help, click Help Topics...'); 7/10/11: baslik, File dugmesi, Home/View sekmeleri, serit (Araclar, Sekiller, Renkler, Boyut). Material/AMOLED: sade arac seridi. [txt-save] TXT duzenleyici: Kaydet solda ve 'Kaydedildi' bildirimi verir, sonra Farkli kaydet; 'TXT olarak kaydet' .txt dosyalarda gizli (iki benzer dugme kalmadi); Word gorunumunde de .txt icin 'TXT olarak kaydet' gizli. derlenmedi
- [fileops-notif] Kopyala/Tasi/Kalici sil: FileJobs + FileOpService (on plan servisi, dataSync): 2 MB ustu kopya ve 40+ ogeli silme ilerleme bildirimi gosterir (yuzde, Iptal), Pulse arka plana atilsa da surer, bitince sonuc bildirimi. Sikistirma (ZipService), indirme ve kurulum (Installing) bildirimleri zaten vardi. [notepad] TXT duzenleyici Windows looklarinda Not Defteri: baslik cubugu (* degisti), menu (Dosya Duzen Bicim Gorunum Yardim: kaydet, farkli kaydet, geri al, kes/kopyala/yapistir, saat/tarih, sozcuk kaydir, yazi boyutu, durum cubugu), satir/sutun durum cubugu (95'te yok). Win11'de Fluent ikon. [paint-fluent] Paint: Windows 11'de gercek Fluent arac ikonlari (paint_* anahtarlari FluentPaths'e indirildi). derlenmedi
- [self-grant] SelfGrant: root/Shizuku varken Pulse kendine POST_NOTIFICATIONS, READ_MEDIA_* (pm grant) ve bir kez/surum: tum dosyalar, ustte goster, kullanim erisimi, bilinmeyen kaynak, sistem ayarlari yazma (appops allow) verir; kamera/konum/rehber/SMS'e dokunmaz. Root yoksa bildirim izni ilk acilista bir kez sorulur. [txt-everywhere] ofOpenUri: dosya URI'li metin dosyalari (txt, json, md...) her yoldan metin duzenleyicide (Windows looklarinda Not Defteri) acilir. derlenmedi

- Build OOM (Kotlin daemon 1.5 GB) -> CI now passes -Xmx6g; added termux-build.sh. derlenmedi

- Winamp widget: queue restored when a widget starts the process, live 1 s redraw, ARGB 900k px crisp scaling; Material player: draggable sheet, lyrics over cover, level bars, top actions; Notepad grey in Win11; filled Fluent icons in Apps card; Store entry removed from Apps menu. derlenmedi

- Poweramp seek drag works around the bars (+48 dp touch area); rated songs keep their filled stars on the cover pill. derlenmedi

- Release builds are signed with the fixed ci/pulse.keystore (CI used a new random debug key per run, so APKs never installed over the old app). Bump 0.10.149. derlenmedi

- Office 365 chrome solid grey (translucent layers over a black window hid dark text), Apps menu lists the standalone apps again, coloured Fluent icons in Settings > Apps, 0.10.150. derlenmedi

- Winamp widget whole-pixel scaling, see-through letterbox, touch areas in screen pixels (were in bitmap pixels); music widget opacity setting, Fluent transport glyphs, prev/next wrap; Poweramp widget opens the player (no animation), lyrics clipped to the cover; AMOLED look works in LookHost for extra apps and the text editor (was falling back to the global look), Notes default AMOLED; 0.10.151. derlenmedi

- Win11 "Fluent colour" icon style draws coloured filled Fluent glyphs everywhere PIcon is used (Line keeps thin glyphs), Extra apps entry in the top-right Apps menu, screen cover clock styles with previews and 9 positions, 0.10.152. derlenmedi

- Each standalone app in Settings > Apps has its place (top-right menu, left menu, Settings only); Windows 11 tool and extra-app pages are see-through (pageBg); Screen cover gets the back arrow bar; 0.10.153. derlenmedi

- Colour Fluent pictures (Fluent UI colour set, PNG) for every app, tool and page in Settings > Apps and the left menu; tapping an app name opens it; Poweramp now playing without suggestions and without scrolling; Material player: colour fades, title slides by direction, art crossfade, play squeeze; removed stray Python wheels from the source folder; 0.10.154. derlenmedi

- Storage walk (folder tree, file types) saved to filesDir/storage_scan.bin and reused at every start; only Refresh walks again; root view picked by a folder that lists with content (isolated mount namespaces showed app data as empty stubs); 0.10.155. derlenmedi

- Icons back to the 0.10.151 Fluent set by default; coloured Fluent is now its own choice (Fluent / Fluent colour / Line); standalone apps leave the top-right menu by default (Extra apps lists them); Office strips pick their ink from the strip colour (no black on black); installer streams APKs into a pm session through cat (pm could not open the private cache), Shizuku copies to shared storage first, a zip of several apps becomes one install per app; 0.10.156. derlenmedi

- Windows 11: Material icons with a twin in the coloured Win11 pack (assets/icons/win11) draw that picture; text editor AMOLED black (View > Black in Notepad, moon button in the plain editor, remembered); Notepad inks taken from the colour under them; 0.10.157. derlenmedi

- Music widget transport back to the Material glyphs (Fluent only with the Windows 11 widget theme); /data/data list read once, app icons load off the main thread; Folder tree at "/" measures each folder with du through root (no endless walk); Winamp and visualizer widgets fill the widget edge to edge; Windows 11 Settings > Apps uses the colour Fluent pictures by default; 0.10.158. derlenmedi

- Cover search ignores compilation names and tag noise (No.1, Classics, Remastered ...), short names must match whole; Excel dark pages: dark cell text switches to the page text colour; Settings > Apps place icons use the Windows 11 pack; 0.10.159. derlenmedi

- Files: a long folder (/data/data) is composed in slices of 60 every 50 ms and skips the per-row item count under /data; 0.10.160. derlenmedi

- Top-right Apps menu lists the switched-on apps again plus the tools (Toolkit moved there, empty Tools group hidden), Fluent colour pictures for tool/app menu icons; Notes transparent background setting (default on); Scan document: page cut-out with perspective straightening, colour / grey / B&W looks, paper shadow; Storage by type: tap opens the file, tighter rows, bigger cards; 0.10.161. derlenmedi

- Icons: four groups (menus/pages/buttons, apps, tools, icons inside screens) each with its own style in Settings > Look (collapsed rows); original = old Windows 11 pack where it has the icon, new Fluent glyph where not; everything merged to main; 0.10.162. derlenmedi

- Calculator extra app (Windows 11 look default, classic keys in 7/XP/95), network type tool + Quick Settings tile (root: 5G only / LTE / auto cycle), contacts info button removed, installer: zip entries share via FileProvider cache paths, original Winamp base-2.91 skin as default, music widget settings sheet (transparent window, live preview, shape, text size, cover size/side, controls, accent, opacity); 0.10.163. derlenmedi

- Equalizer: attach falls back to the global mix, keeps a working equalizer when the session id is unset, shows the reason and a Try again button instead of a bare block icon; 0.10.164. derlenmedi

- Office 365 window (Word, Excel, PowerPoint, PDF): dark grey strips with white text and icons in the light and dark look; 0.10.165. derlenmedi

- Music: artist shown everywhere (Winamp "Artist - Title", Poweramp chips, online tags on by default); separate lyrics notification option; lyrics index read once (LyricsIndex) + 3-day miss cache + shorter timeouts; lyrics sheet darker, closes to the cover after the last line; search words highlighted; covers cached in memory (no stale/other covers while swiping, neighbours only while dragging); Material mini player with shuffle, repeat and a round play button; 0.10.166. derlenmedi

- Calculator: keys drawn per look (round in AMOLED/Material, bevelled 95, glossy XP/7, rounded 11) + look button; folder tree reads Android/data through root and shows the age of the saved scan; Winamp widget picker shows 3 widgets, skins folded; Poweramp widget redrawn (starfield, rounded cover, title/artist/album, loop/shuffle + n/total, 5 grey buttons incl. rewind/forward 10 s); music widget settings in white text + shuffle/repeat option; Material player shuffle/repeat long-press menus (tap = on/off); lyrics: same-name .lrc/.txt in any letter case and in the usual lyrics folders; Poweramp search opens the keyboard; 0.10.167. derlenmedi

- Shuffle in the Material player now shuffles the queue once into a fixed order (off restores the old order) instead of random picks at each skip; 0.10.168. derlenmedi

- Network type: 3G/2G/LTE modes also set the phone service allowed-types bitmask (cmd phone), not only the old setting; Excel/Office: content area and grid clipped to their bounds (top chrome was painted over); Screenshot/record tile: tap = screencap screenshot as root (reads FLAG_SECURE windows), long press = recorder page; recorder: show touches, floating pause/stop/pen/grab-frame bar; 0.10.169. derlenmedi

- Files: cut / copy / paste buttons of the selection bar 1.75x bigger; cutting out of an APK/zip no longer refused for the signature; 0.10.170. derlenmedi

- Files: cut/copy/paste bar pinned at the top while items are picked; pull down to refresh; Poweramp waveform taller so it shows beyond the transport buttons; Material player cover corners 24dp; 0.10.171. derlenmedi

- Calculator colour Fluent picture; Toolkit "DPI bypass and free VPNs" (encrypted DNS presets + free VPN list); Screen cover: brightness slider, open window with draggable edges (four bands), clock on one line; 0.10.172. derlenmedi

- Screenshot: when screencap comes out black (secure window) the other root ways are tried (each display id, the plain binary) and the result says if the app still blocks it; lyrics: root reads same-name .lrc/.txt when the folder is not readable; Windows 7 launcher orb draws the Windows flag; 0.10.173. derlenmedi

- Launcher: the apps list of Windows 7, XP and 95 is an Explorer-style window (title bar with caption buttons, address bar with search, group navigation pane, icon grid, status line); 0.10.174. derlenmedi

- Lyrics notification: lines taken 0.7 s ahead and refreshed twice a second (the collapsed one trailed), current line in bold in both notifications; 0.10.175. derlenmedi

- Lyrics notification: bold through Unicode bold letters, arrow back (text style); lyrics sheet: second lookup like the notification, no auto close when opened past the last line; calculator round keys stay round; Files selection bar buttons all the same size; 0.10.176. derlenmedi

- Player: swipe up opens the lyrics (it used to slide the player away), lyrics of this and the next two songs looked up in the background, seek bar glides between ticks; Toolkit tiles all the same height; 0.10.178. derlenmedi

- Toolkit "DNS and TTL": VPN app list removed, TTL setter (root); screen cover brightness slider reaches the panel maximum (unset = system); 0.10.179. derlenmedi

- Lyrics sheet: "Searching for lyrics…", when none is found artist/song fields and Search again; setting "Save found lyrics as .lrc beside the song"; 0.10.180. derlenmedi

- Lyrics: plain lyrics are saved as .lrc too, saving the .lrc file is on by default, optional MP3 tag (USLT) writing as a switch; 0.10.181. derlenmedi

- Equalizer: when the system effect cannot be created a five-band equalizer inside the player (SoftEq) takes over; lyrics search is fuzzy (clean title, each artist of a comma list, best look-alike among results); White noise: the "Stream" loop removed; 0.10.182. derlenmedi

- Music settings: load a .poweramp-backup (playlists, ratings, play counts, album_art covers by "artist - title", optional MP3 APIC writing); Files: unknown file types ask how to open (archive / text / image / another app); notification-access and accessibility entries get clear names; 0.10.183. derlenmedi

- Launcher widget picker lists Pulse's own themed widgets first; 0.10.184. derlenmedi

- Launcher (Windows looks): This PC, Recycle Bin, Paint, Task Manager and Command Prompt open as framed windows (title bar, caption buttons, frame in the look of 95/XP/7/10/11) over the launcher; FramedToolActivity; 0.10.185. derlenmedi

- Launcher desktop: Control Panel, Performance, Services, Startup apps and Programs shortcuts (framed windows in the Windows looks); 0.10.186. derlenmedi

- Windows media player widget (95 CD Player, XP, 7 Aero, flat cards); network type shows the phone's answer; Back icons in App details / Leftovers / Permission audit; launcher Log off (windows, Win10 rail, Win11 menu); Control Panel with 17 items in 5 groups opened from the launcher; 0.10.187. derlenmedi

- Launcher: framed windows open centred and the middle caption button grows them to full screen; Minesweeper and Solitaire drawn in the look of the theme; music settings in Poweramp-style categories with icons; lyrics page shows what the lookup saw when nothing is found; 0.10.188. derlenmedi

- Files: the top-right settings button is gone (Settings stays in the menu, sections collapsed), gaps between files and folders cut to a third; 0.10.189. derlenmedi

- Poweramp now playing: lyrics open and close by their own calls (the menu entry opened and shut it), a vertical drag on the cover moves the whole page (down closes, up opens lyrics); Widgets search finds the Windows media player and shows Winamp skins that match; 0.10.190. derlenmedi

- Launcher desktop: a clock gadget per Windows look (round Aero gadget for 7, Luna panel, 95 digital, light digits for 10/11), Windows 11 wallpaper from the app's own pictures, Games hub with FreeCell; 0.10.191. derlenmedi

- Music: shuffle and repeat are saved and come back after a restart or an update (and the fixed-order shuffle flag); lyrics widget line in bold letters; 0.10.192. derlenmedi

- Poweramp lyrics: sheet is a layer over the whole page, the lyrics button also for songs whose lyrics were found online, no grey diagnostic line; 0.10.193. derlenmedi

- Player animations slower and softer (sheet, title slide, cover settle, lyrics sheet); Downloads: a web page link is read for its download links (plain fetch, no WebView) and the files found are listed; 0.10.194. derlenmedi

- A link shared to Pulse opens a small window over the sharing app (Material sheet or Windows dialog box, own look) instead of the app; Settings > Pages in Windows 11 uses the colour Fluent pictures unless the line style is chosen; 0.10.195. derlenmedi

- Installer: option to leave out the CPU splits of other ABIs in bundles (a single APK cannot be cut without re-signing); install window buttons follow the window look (text buttons in Material); 0.10.196. derlenmedi

- Long press on the Screen cover tile opens its settings page; 0.10.197. derlenmedi

- Fix: size import in Downloads; 0.10.198. derlenmedi

- Screen cover can be drawn as an accessibility overlay (topmost layer, above bars and keyboard) when "Pulse · Screen cover layer" is on; backup branch backup-2026-10-22 of 0.10.198; 0.10.199. derlenmedi

- Phone wallpapers for Windows 11 (18), Windows 10 (10 + 2), 7 and XP (portrait, 960 wide, 3 MB), rotated screens use the desktop ones, also as Pulse background (Win11 pack17-34); launcher: desktop windows are draggable and several at once over the launcher, desktop icons chosen in settings (This PC, Recycle Bin, File Explorer by default, Pulse apps addable), lyrics back over the cover, smaller counter pill; 0.10.200. derlenmedi

- Windows 10 Mobile launcher: action center (quick tiles, Collapse, no notifications) pulled down from the top edge; Poweramp now playing shows the library underneath while it is pulled down; 0.10.201. derlenmedi

- Launcher: Win10 Black and colour themes removed, look picker with pictures, no default dock apps, top-right buttons optional, windows minimise to chips and resize, widgets movable/resizable/removable on the Windows desktops, clock flyout (calendar, quick tiles, volume), File Explorer as a window, orientation-specific wallpapers, Poweramp widget stars switch, lyrics button always on the Poweramp cover; 0.10.202. derlenmedi

- Fix: Arrangement import in the launcher settings; 0.10.203. derlenmedi

- 0.10.204 derlenmedi: önerilerden çalma sırası (albüm/şarkı), çubuk tuşuna basılı tutunca tam ekran görselleştirme, bildirim düğmesi önizleme kartı, Win11 Mica / Win7 Aero saydam pencere çerçevesi.

- 0.10.205 derlenmedi: Pulse a ayrı "Mica (new)" ve "Acrylic (new)" görünümleri (Win11 kopyası, efekt ayarını okumaz, duvar kağıdı bulanık cam, açık/koyu).

- 0.10.206 derlenmedi: ekstra uygulamaların başlatıcı ikonları ayrı (siyah zemin, beyaz çizgi); ekran kapağı çift dokunuşla kapanınca hızlı ayar kutucuğu da kapanır.

- 0.10.207 derlenmedi: masaüstü pencerelerinde gerçek bulanık Mica/Aero zemin, Mağaza penceresi, sekmeli Dosya Gezgini, Denetim Merkezi (parlaklık/ses/hızlı kutular), Windows Phone Ayarlar sayfası.

- 0.10.208 derlenmedi: ayarlarda Tema A-D duvar kağıtları kaldırıldı, yatay + telefon resimleri tek listede; küçük önizlemeler arka planda düşük kalitede çözülüyor (kasma azalır).

- 0.10.209 derlenmedi: indirme silme onayı; Windows Phone simge/kutucuk ayarı (varsayılan düz simge) + uzun basınca parmağın yerinde açılan menü; Win11 masaüstü penceresi koyu Mica; sözler: DNS engelinde DNS-over-HTTPS yedeği + hata satırı.

- 0.10.210 derlenmedi: 0.10.209 derleme düzeltmesi (FramedWindow virgül).

- 0.10.211 derlenmedi: sözler önce cihazdaki .lrc/.txt (adı şarkıya benzeyen veya başlığı içeren dosya dahil) sonra internet; öneri mix satırlarının yanında çal tuşu.

- 0.10.212 derlenmedi: Poweramp yedeği: aynı adlı dosyalarda klasörle eşleştirme, uzantı değişmiş şarkıları bulma, boş kütüphane uyarısı.

- 0.10.213 derlenmedi: dosya işlemi bildirimleri 4 sn sonra kendiliğinden gider; Poweramp tarzı animasyonlar: durunca küçülen/yaylanan kapak, yumuşak inen çubuklar, büyüyüp renklenen/uzaklaştıkça sönen söz satırları.

- 0.10.214 derlenmedi: söz sayfasında satırlar beyaz (koyu zeminde tema grisi görünmüyordu: asıl neden); söz araması 10 dk hatırlanır, sonuçta albüm kapağı; söz bildiriminde albüm kapağı.

- 0.10.215 derlenmedi: Poweramp: kendi veritabanından (root) tüm puan ve dinlenme sayıları + album_art resimleri; yedek eşleşmesinde Unicode (ü/ı vb.) düzeltmesi.

- 0.10.216 derlenmedi: Win11 launcher düz koyu siyah; masaüstü saati taşınır/kaldırılır widget kartı; basılı tutma menüsü parmağın sağında/solunda küçük popup (boş alan dahil); akıllı karıştırma (ruh hali/enerji/puana göre, az-çok rastgele ayarı); söz bildirimi: harf atlamayan kalın + tüm satır.

- 0.10.217 derlenmedi: Dosyalar: sağ kenarda hızlı kaydırma tutamacı, Android/data-obb-media klasörlerinde uygulama simgesi, klasörden çıkıp dönünce kaldığın konum, arşivden kesilemezse çıkar + yeniden sıkıştır kutusu.

- 0.10.218 derlenmedi: Dosyalar: klasör boyutları simgesine basılı tutunca ağaç içinde bulunulan klasörde açılır.

- 0.10.219 derlenmedi: hızlı ayar kutucuklarına basılı tutunca: DNS kendi Toolkit DNS aracını, Ekran yönü kendi aracını açar (launcher/ayarlar açılmaz, ekran yönü artık sessizce kapanıp launchera düşmez).

- 0.10.220 derlenmedi: metin düzenleyicide Kaydet ve Farklı kaydet dışındaki iki simge (karanlık mod, TXT olarak kaydet) kaldırıldı.

- 0.10.221 derlenmedi: söz gösterimi baştan yazıldı (PlayerLyrics: AnimatedVisibility, ölçüm/offset yok, önce cihazdaki sözler); kapağı aşağı çekip bırakınca ışınlanma giderildi, geri dönüş yaylı.

- 0.10.222 derlenmedi: söz bildirimindeki ok emoji değil düz karakter (►).

- 0.10.223 derlenmedi: kapak kaydırmada komşu kapaklar çalma sırasını (karıştırma dahil) gösterir; düz ve hızlı kayma (eğik çark kalktı).

- 0.10.224 derlenmedi: söz bildirimi vurgusu ayarı (başlık altı çizili / ince satır, açılınca kalın / hep kalın).
