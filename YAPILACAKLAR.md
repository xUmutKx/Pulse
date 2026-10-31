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

- Pulse: Beyaz gürültü artık müzik çaların içinde (ses ikonu -> WhiteNoiseHub tam ekran); ayrı launcher/ayar/mağaza satırı kaldırıldı. derlendi (1.0.274).
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

- 0.10.225 derlenmedi: Poweramp yedeği: başlık/sanatçı ile eşleşme + bulunamayanlardan örnek ad, .poweramp-backup dışa aktarma (aynı lists-export şeması); müzik ayarları: ilk sayfa yalnızca kategoriler, kategori tam sayfa + sol üstte geri; kapağı çekerken kütüphane yumuşak belirir.

- 0.10.226 derlenmedi: yüzen söz araması çökmesi (geri tuşu sahibi yoktu); bildirim kanallarında rozet yok; resimler (HEIC dahil) doğrudan Samsung Galeri ile, Birlikte aç sistemin "Bir kez/Her zaman" seçicisiyle; görselleştirme basılı tutunca 6 stil menüsü (varsayılan üstte).

- 0.10.227 derlenmedi: albüm kapağı aramasında sanatçı/albüm alanları + daha geniş yedek aramalar; klasör boyutu ağacı basılı tutuş açılışında tek geri ile çıkar; söz araması sonuçları diskte hatırlanır.

- 0.10.228 derlenmedi: yedek yalnızca ayarlar + küçük kullanıcı verisi (çalma listeleri, notlar, profil...), kopyala/kes dosyaları ve kapaklar girmez; Ayarlar > Uygulamalar kataloglara ayrıldı, her uygulamaya ana ekrana ekle (raptiye).

- 0.10.229 derlenmedi: yeni ekstra uygulama LocalSend (v2 protokolü: çoklu yayın + alt ağ taraması ile cihaz bulma, dosya gönder/al, kabul penceresi; Material varsayılan, AMOLED/Windows seçilebilir).

- 0.10.230 derlenmedi: Mix yapıcı (ana sayfa altı: ruh hali, kaynak, adet); Poweramp/Windows oynatıcı widget: arka plan şeffaf/siyah/yıldızlı + kapak boyutu (varsayılan %75); müzik widget önizlemeleri düz beyaz; geçici kopyalar (arşiv kes/kopyala) ayarlanabilir süre sonra (1 saat) silinir.

- 0.10.231 derlenmedi: 0.10.230 derleme düzeltmesi (horizontalScroll importu).

- 0.10.232 derlenmedi: Win7 tarzı Not gadget (masaüstü, klavyeyle yazılır, çevrilir/renk); YouTube/YT Music çalma listesi doğrudan YouTube sayfasından okunur (Piped/Invidious ölse de), ayarlarda ayrı satır.

- 0.10.233 derlenmedi: klasör boyutlarında 3. görünüm (disk haritası: alanı boyutuyla orantılı bloklar), düğme 3 görünümü döndürür; müzik ayar kategorileri yuvarlak renkli simgeler; ana ekrana eklenen kısayollar uygulamanın kendi simgesiyle; bilinmeyen dosyada ve menüde Farklı kaydet.

- 0.10.234 derlenmedi: PDF/Office yan çevrilince çubuklar gizlenir (dokununca 2 sn görünür, ayar); tam ekran sözler ayarı (üstte kapak/şarkı/sanatçı); Toolkit: yüzen sayaç (kronometre + geri sayım).

- 0.10.235 derlenmedi: 0.10.234 derleme düzeltmesi (importlar).

- 0.10.236 derlenmedi: ekran kapağı için Gelişmiş mod (StandBy tarzı): sayfalar (dijital/analog saat, tarihli saat, takvim, pil, çalan şarkı, ikili sayfalar), yazı tipi, renk, 24 saat, saniye, ekran koruma kayması, gece kırmızı modu, elle tetik (hızlı ayar kutucuğu StandBy) ve şarjda otomatik tetik; Temel/Gelişmiş geçiş kapak ayarında.

- 0.10.237 derlenmedi: sistemde açma/paylaşma listesinde "Pulse · Save to…" (bir dosyayı ad+klasör seçip kopya kaydet); ekran görüntüsü/kayıt kutucuğu kamera+köşe simgesi, basılı tutma küçük popup (görüntü + kayıt), ekran görüntüleri DCIM/Screenshots, root yoksa erişilebilirlik ile ekran görüntüsü; ana Ayarlar yuvarlak renkli kategori listesi.

- 0.10.238 derlenmedi: müzik ayarları tam ekran sayfa (kategorili); launcher ayarları kategori listesi + tam sayfalar; launcher Ekstralar: çift dokunuşla kilit, aşağı kaydır bildirim, yukarı kaydır uygulamalar, simge şekli, günlük duvar kağıdı, karartma, bulanıklık, 24 saat, tarih biçimi, saniye, ekran açık, titreşim, arama klavyesi, düzen kilidi, ayar dışa/içe aktar (15 özellik).

- 0.10.239 derlenmedi: 0.10.238 derleme düzeltmesi (importlar).

- 0.10.240 derlenmedi: Bluetooth otomatik çalma artık oynatıcı kapalıyken de (manifest alıcısı + servis başlatma); widget sanatçısı: etiket/kütüphaneden okunur, "Bilinmeyen sanatçı" yerine gerçek ad.

- 0.10.241 derlenmedi: Pulse ayarlarında kategori ikon dairesi kaldırıldı; Win11 görünümde yinelenen duvar kağıdı ızgarası kaldırıldı; Poweramp root içe aktarma doğru root görünümüyle (hata nedeni gösterilir); kapak aşağı çekilince altta gidilecek sayfa (kütüphane/arama) hazır, ışınlanma yok; Kulaklık/Bluetooth ayarları (kablolu kulaklıkta devam, çıkınca duraklat, USB DAC, ses odağı, tuşlar); LocalSend Fluent menü ikonu, tüm arayüzlerde + ARP/USB/hotspot taraması, Fluent yükleme halkası.

- 0.10.242 derlenmedi: LocalSend Paylaş listesinde (Quick Share alternatifi, cihazlar orada görünür, dokununca gönderir) ve Quick Settings karosu; root ile ekran görüntüsünde root artık kontrol edilir (siyah çıkması FLAG_SECURE ise root da aşamayabilir); ana ekran ayarlarında kategori ikonu arkaplanı kaldırıldı, uzun yol değeri başlığı ezmiyor; Windows 7 klasik Başlat menüsü (programlar, Tüm programlar, arama, sağda Belgeler/Resimler/Müzik/Oyunlar/Bilgisayar/Denetim Masası..., sağ altta Oturumu kapat); masaüstü widgetlarında kalıcı çarpı yok, uzun basınca düzenleme modu (taşı/kaldır/boyut/seçenekler), saat de aynı; masaüstü notu gerçek Yapışkan Not widget olarak eklenir; Windows Phone tek siyah görünüm + otomatik farklı boyutlu kutucuklar.

- 0.10.243 derlenmedi: Gelişmiş kapakta (StandBy) iOS StandBy uygulamasındaki saat fontları (köşeli segment ekranlar, dar, serif, neon vb. 40 font) rakam seçeneği oldu; ayarlarda her sayfa ve her font canlı küçük önizleme olarak görünür, dokununca seçilir.

- 0.10.244 derlenmedi: StandBy uygulamasının APK sinden alınanlar: ekran listesi (Segmented, Big Crop, Retroflip, Neon, Matrix, Pixel, Roman, Word, Solar Watch, Radial, Analog Clean/NoMarkers/Square, Clock of Clocks, World, Dementia, Sereno, Battery Dots/Wave, Charging, Stopwatch, Timer, Pomodoro, Quotes, Striking Calendar - 27 yeni sayfa), 38 Big Crop + 15 Retroflip + 12 Solar renk paleti ve stil seçici; launcher önerileri: ana ekranda klasörler + sürükle-bırak + sayfalar arası taşıma, masaüstü simgelerini istediğin yere sürükle, hızlı arama (aşağı kaydır) + son açılanlar, simge paketi desteği (Nova/ADW), bildirim rozetleri, Win10/11 başlatta canlı kutucuklar, görev çubuğunda pencere düğmeleri (95/XP/7/10/11), Windows Phone kutucukları varsayılan açık.

- 0.10.245 derlenmedi: Gelişmiş kapak ayarları düzenleyici oldu: üstte seçili sayfanın büyük canlı önizlemesi, altında Sayfalar / Stil / Renk / Diğer sekmeleri, her seçenek küçük canlı resimlerle.

- 0.10.246 derlenmedi: 0.10.244/245 derleme düzeltmesi (font kaynakları, matchParentSize importu).

- 0.10.247 derlenmedi: Poweramp yedeğinde olmayan şarkılar "atlandı" diye bildirilir; Poweramp oynatıcıda kapak ekran kenarına daha yakın; Poweramp widget kapağı varsayılan %86 (+%15) ve widget ayarları (köşeler, kapak gölgesi, yuvarlak kapak); LocalSend aktarımında duraklat/iptal ve sürüklenebilir yüzen balon (ilerleme halkası, dokununca kart); Windows Phone kutucukları her zaman kare (küçük/orta/geniş), uzun basınca boyut, ayarlarda düzen.

- 0.10.248 derlenmedi: yedek yüklenince "telefonda yok" mesajı kalktı; Favoriler yıldıza göre (çok yıldız önce, sonra ada göre); Ses bilgisi penceresinde ve parça menüsünde "Düzenle / Etiketleri düzenle"; YouTube anahtarı kaynak koddan kalktı (sayfadan okunur).

- 0.10.249 derlenmedi: 0.10.248 derleme düzeltmesi (Edit ikonu importu); gelişmiş kapakta: Apple stili (Inter) font, kendi font dosyam (örn. SF Pro), yalnız kenar çizgisi (içi boş rakam), elle karıştırılan renk; gelişmiş modda temel kapağa ait düğmeler gizlendi; Poweramp yedeği ayarlarda Kütüphane altına taşındı, doğrudan içe aktarma veritabanı sütunlarını okuyarak çalışır ve hata nedenini yazar.

- 0.10.250 derlenmedi: gelişmiş kapakta telefon dikeyken saat sayfaları düzeltildi: dijital, segmentli, neon, matrix, piksel, çevirmeli, saatlerden saat yatay yerine saat üstte dakika altta dizilir ve ekranı doldurur.

- 0.10.251 derlenmedi: gelişmiş kapak ayarlarında "Şimdi dene" kaldırıldı (zaten anahtar açıyor), parlaklık da yalnız temel kapakta.

- 0.10.252 derlenmedi: Müzik çalarda (Material) şarkı kapağı %10 büyüdü.

- 0.10.253 derlenmedi: Authenticator sayfasına geri tuşu ve başlık (Pulse içinde üst çubukta, kendi uygulaması olarak açılınca sayfanın başında).

- 0.10.254 derlenmedi: Authenticator: sistem parmak izi / ekran kilidi istemi (ayarlardan açılır), hesapları Pulse yedeğine koy (kendi parolanla şifrelenir, geri yüklenince parolayla alınır), sayfada ayar tuşu.

- 0.10.255 derlenmedi: Dosya yöneticisi: yeniden adlandırınca ad seçili + klavye açık, çıkarma/kopyalama çubuğu (yüzde, animasyon, X ile iptal), sıkıştırma çubuğu X, klasör boyutları adın altında + boyuta göre sıralama klasörleri de içerir, Files sekme değişince kaldığı klasörde kalır, ağaçta Kök gerçekten "/", yeni klasör "boş" görünmez, "bu klasörü kullan" ikon. Hesap makinesi: tuşlar alanı doldurur, menü (standart/bilimsel/grafik/dönüştürücü(döviz+birimler)/programcı + görünüm), grafik çizimi, canlı kur.
- 0.10.256 derlenmedi: Poweramp widget ayar ekranı (kapak +10%, widget seçenekleri), masaüstünde Windows Ayarlar ve Denetim Masası, Windows Phone besleme sayfası + dolu dock, Clippy (eski Windows görünümleri), Nova tarzı ana ekran ayarları (satır sayısı, etiketler, sayfa noktaları, sayfa efekti, klasör simgesi, dock simge sayısı, rozet stili).
- 0.10.257 derlenmedi: Windows başlatıcı görünümlerinde isteğe bağlı açılış (boot) ve giriş ekranı (ayar: Başlatıcı ayarları > Ekstralar); sağ üst Uygulamalar menüsünde Hesap makinesi her zaman listelenir.
- 0.10.258 derlenmedi: Başlatıcı duvar kağıdına hareketli deniz dalgası efekti (Ekstralar > Duvar kağıdında deniz dalgaları).
- 0.10.259 derlenmedi: derleme düzeltmesi (ControlPanel getValue/setValue importları).
- 0.10.260 derlenmedi: deniz dalgası kaldırıldı (yanlış anlaşılma); yerine başlatıcı ses efektleri: görünüme göre açılış sesi + tıklama (Ekstralar > Ses efektleri).
- 0.10.261 derlenmedi: APK yükleyici ayarlarında anahtarlar Material 3 anahtar; yeni "Yeni kurulumları küçült" seçeneği (tek APK: kullanılmayan işlemci kütüphaneleri silinir, Pulse anahtarıyla yeniden imzalanır, sadece kurulu olmayan uygulamalar). Ses efektleri: kullanıcı zip gönderecek, yeni ses eklenmeyecek.
- 0.10.262 derlenmedi: başlatıcı sesleri gerçek Windows sesleri (95/XP/7/10/11 açılış + menü tıklaması, res/raw), Ekran kapağı ayarlardan ve Ekstra uygulamalar listesinden kaldırıldı (Toolkit de var), Windows 11 simge stili seçimi yazı yerine örnek simgeler.
- 0.10.263 derlenmedi: PULSE KLAVYE (yeni extra uygulama, kendi launcher girişi + ayar ekranı): SwiftKey tarzı, harf/sayı/sembol sayfaları, EN/TR/DE/FR/ES düzen, yazım hatası toleranslı otomatik düzeltme (komşu tuş + aksan maliyeti, 35k kelime EN/TR), öneri çubuğu (3 slot), geri tuşunda düzeltmeyi geri al, hızlı nokta, otomatik büyük harf/boşluk, boşlukta imleç kaydırma, silmede hızlı seçip silme, emoji paneli, araç çubuğu + 3x4 menü (AMOLED siyah), pano, ok tuşları satırı, sayı satırı, boyut/alt boşluk/tek el, temalar (AMOLED, Material, Win 11/10/7/XP/95, özel renkler + sıfırla), fabrika ayarları.
- 0.10.264 derlenmedi: derleme düzeltmesi (LauncherSounds aşırı yükleme, PSlider onDone).
- 0.10.265 derlenmedi: gelişmiş ekran kapağı: sayfa sırası (ok tuşlarıyla yer değiştir), kapak açıkken Geri tuşu arkadaki uygulamaya gitmez (yalnız çift dokunuş kapatır).
- 0.10.266 derlenmedi: Klavye: kaydırarak yazma (iz çizgisi), yukarı kaydırınca sembol, animasyonlar (tuş basma, panel geçişi, balon), ek tuş satırı (Esc/Tab/Ctrl/Alt/Win/Shift/oklar/Home/End/PgUp/PgDn/Del/F1-F12, Ctrl+harf kısayolu), düzenlenebilir araç çubuğu ve sıralama, menü 4x4 (geri al/yinele). Ekran kapağı: ekrana dokunulmayınca otomatik açılma süresi (erişilebilirlik dokunma olayları). Poweramp widget: karıştır/döngü simgeleri ve dokunma alanı büyütüldü.
- 0.10.267 derlenmedi: Klavye: daha uzun tuşlar (varsayılan %110, 58dp), üç nokta/menü tekrar basınca kapanır + sol üst düğme panelden döner, simgeler temaya göre (dolu/keskin/yuvarlak/çizgi), Türkçe F düzeni, üst çubuktan aşağı çekince klavye kapanır, boşlukta basılı tutmadan da kaydırarak imleç (zamanlayıcı hatası düzeltildi), sözlük indirme (en/tr/de/fr/es), Türkçe sözlük 130 bin kelime (ek almış biçimler), menü öğeleri düzenlenebilir, ayarlarda klavye altta canlı önizleme (panel/menü açılır), Enter tuşu diğer tuşlar gibi, yeniden boyutlandırma klavyenin kenarlarından sürükleyerek, ek tuş satırı boşluk satırının altına konabilir.
- 0.10.268 derlenmedi: Ekran kapağı karosuna basınca hızlı panel kendiliğinden kapanır (bildirim gölgesi iki kez kapatılır). Klavye: ok tuşları, Ctrl, Alt, Win satırı varsayılan açık ve boşluk satırının altında (bir kez uygulanır).
- 0.10.269 derlenmedi: Hesap makinesi Material/AMOLED: yuvarlak tuşlar (en çok 84 dp, altta), bellek tuşları kalktı, ( ) tuşu, üstte geçmiş + dönüştürücü + bilimsel simgeleri, geniş ekran (eski işlem ya da anlık sonuç üstte). Ekran kapağı: saat/pencere boyutu ayarı (%40-140). Dosya: sıkıştırırken parçalara bölme (MB/GB, ad.zip.001, .002 ...; 7-Zip ve bu uygulama ilk parçayı açar).
- 0.10.270 derlenmedi: derleme düzeltmesi (ExplorerZip Alignment).
- 0.10.271 derlenmedi: Grafik hesap makinesi 3D (z = f(x,y) yüzeyi, döndürme + yakınlaştırma, 3D düğmesi). Klavye açılınca ekran klavyenin üstüne gelir (imePadding: ana ekran, ekstra uygulamalar, notlar, müzik, mesajlar, resim düzenleme). Klavye: açık olan tüm dillerin sözlükleri birlikte çalışır (Türkçe QWERTY + İngilizce otomatik düzeltme), sözlüğü olmayan dil kendi sözlüğünü indirir.
- 0.10.272 derlenmedi: Müzik ayarları: kategori değişince gruplar kapalı başlar, Bildirim ayrı kategori (açık tuşlar + örnek bildirim). Klavye: basınca gri, ipucu harfleri gri değil, üst çubuktan aşağı çekince klavye parmakla iner ve kapanır, tuş satırı düzenleyici (basılı tut + sürükle sırala, dokun + × kaldır). Ekran kapağı: iki yeni yüz (çizgili dijital, çizgili analog), düzenleyicide yatay + dikey ayrı önizleme.
- 0.10.273 derlenmedi: derleme düzeltmesi.
- 0.10.274 derlenmedi: ekran kapağı tam ortada: "hafif kay" (ekranı koruma) varsayılan kapalı ve en çok ±2 dp (önceden ±9 dp sağa/sola kayıyordu).
- 0.10.275 derlenmedi: Müzik: 5 resimli Windows Media kaplaması (Blob, Windows 7, Candy, Silver, Luna): gönderilen resimlerden arka planı silinmiş, yazılar silinip canlı yazı/kapak/arama çubuğu/oynat tuşu üstüne çizildi; oynatıcı sayfası (Görünüm > Media Player skins) + her kaplama için ana ekran widget, ekranına dokununca görselleştirici.
- 0.10.276 derlenmedi: İnternet hız testi (araç + hızlı panel karosu): ibreli hız göstergesi, ping/titreşim/indirme/yükleme + sağlayıcı (Cloudflare uç noktaları); karo ölçerken yanar ve halkası döner, genişletilmişse sonucu gösterir; Ekstra uygulamalar listesinde, yeri Ayarlar > Araçlar yerleşiminden seçilir. Müzik bildirimi/widget artık yeni oynatıcı penceresi açmaz, açık olanı öne getirir (ToolOpenActivity). Canlı söz bildirimi kaydırılıp atılırsa birkaç saniye içinde geri gelir.
- 0.10.277 derlenmedi: derleme düzeltmesi (SpeedTestScreen setValue).
- 0.10.278 derlenmedi: Buds yöneticisi (Galaxy Buds, GalaxyBudsClient protokol notlarından): kulaklık seç/bağlan, pil + hangi kulaklık takılı, gürültü denetimi (kapalı/ANC/ortam), ikisi de çıkınca müzik dursun + gürültü denetimi kapansın, takınca eski haline dönsün (arka plan servisi isteğe bağlı). Ekstra uygulamalar listesine (Screen recorder altına) Hesap makinesi, Klavye, Hız testi, Buds yöneticisi, Paint, LocalSend eklendi; her satırın sağında sağ üst ⋮ menüsünde göster/gizle anahtarı.
- 0.10.279 derlenmedi: derleme düzeltmesi (BudsState data class).
- 0.10.280 derlenmedi: Kişiler: ad/soyad/adres alanlarında her kelime büyük harf, listede sağa kaydır = ara (yeşil), sola = SMS (mavi) animasyonlu. Müzik: 3 nokta menüsünden "Edit tags" kalktı (bilgi penceresinde kalem simgesi), Lyrics tuşuna basılı tutunca yanlış sözleri sil, çalarken ses çubukları kayarak iner, ses sıfır olunca duraklat ayarı (Kulaklık/Bluetooth), canlı söz bildirimi Türkçe harfleri de kalın. Poweramp yedeği: bulunamayan şarkılar sayılır + yedekteki adla da eşleşir. Ekran kapağı: Temel/Gelişmiş ayrımı kalktı, tek kapak (varsayılan boş siyah, sayfalar eklenir) + perde animasyonu. Resimli Media kaplamaları 2.5x daha keskin, widget çözünürlüğü yükseltildi.
- 0.10.281 derlenmedi: derleme düzeltmesi.
- 0.10.283: derlenmedi — uygulama listesinde klasörler (Klasöre ekle, uzun bas: ana ekrana ekle/adlandır/sil)
- 0.10.284: derlenmedi — Widget Stack skin görselleri (5 skin), hız testi yeni düzen, Windows hesap makinesi küçük tuşlar altta, Buds kategoriler+açıklama+tüm ses cihazları, Fluent ikonlar (hız testi, buds, hesap makinesi, klavye)
- 0.10.286: derlenmedi — klavye (sadece EN/TR, dil seçimi, virgül uzun bas=sesle yazma, aşağı kaydır kapat, gri/uzun tuşlar), ekstra uygulama menü varsayılanları, hava durumu sahne+ikonlar+kartlar, launcher ayarları Nova kategorileri
- 0.10.288: derlenmedi — quick panel karo animasyonu (TilePop), perde animasyonu ayarı (varsayılan kapalı)
- 0.10.289: derlenmedi — Dosyalar ana sayfası (depolama, son dosyalar/klasörler, türler, alt çubuk)
- 0.10.292: derlenmedi — Poweramp widget: shuffle/loop %5 küçük, transport tuşları %15 yakın
- 0.10.297: derlenmedi — Medya araçları (sıkıştır, GIF düzenleyici, dönüştürücü), Office Farklı kaydet penceresi, widget sayfası WMP skin kartları ve katlanan bölümler, Poweramp oynat tuşu %10 küçük
- 0.10.301: derlenmedi — size-and-compat birleşti (Android 5), SwiftKey kelimelerini root ile al, Poweramp canlı veri içe aktarma düzeltildi (yol, puan ölçeği, çalma listeleri), iki sürüm yayını
- 0.10.302: derlenmedi — uygulama kilidi: balon (bubble) açılınca kilit çıkmaz, Ayarlar > Uygulama kilidi > Balonlar anahtarı (varsayılan kapalı)
- 0.10.303: derlenmedi — Widget Stack: kalan 5 Frutiger Aero kaplama (6-10) ana ekran widgetı olarak eklendi, Widgetlar sayfasında hepsi listelenir; Mesajlar tercihleri (bildirim, kod kopyalama, balon) hazırlandı
- 0.10.304: derlenmedi — Mesajlar Textra tarzı: numaraya/kişiye dokun → Kişiyi aç/Ara/Kopyala, mesaj içindeki numaralar ve linkler dokunulur, MMS resimleri, gelen SMS/MMS bildirimi (yanıtla, okundu, kod kopyala), doğrulama kodu otomatik kopyalama, sohbet balonu, varsayılan SMS uygulaması olma; son uygulamalarda aynı araç tek kart (Müzik çalar iki kez görünüyordu)
- 0.10.305: derlenmedi — söz ekranı tam siyah; albüm kapağı arama (sanatçı + şarkı/albüm alanları, her zaman görünür) Material oynatıcıya da eklendi, Poweramp penceresinde sonuç varken de alanlar var
- 0.10.306: derlenmedi — müzik bildirimi: kapak artık kendi/gömülü/internetten bulunan kapaktan gelir (sadece sistem albüm resmine bağlı değildi), 5 tuş sınırında Kapat tuşu düşmez
- 0.10.307: derlenmedi — Dosyalar: tür açılınca kendi sayfası (en büyük önce, Diğer de açılır), başlıktaki boyut/tür/temizlik simgeleri varsayılan kapalı (Ayarlar ile açılır), SD Maid kural dosyalarından temizlik kategorileri; Buds: ad boşluk/çift cihaz düzeltildi, simge, dokunma/ses/oyun/bul ayarları; Menü: Hesap makinesinden sonraki uygulamalar varsayılan Ekstra uygulamalarda; Başlatıcı: çekmece simge boyutu, ad yazısı boyutu
- 0.10.308: derlenmedi — Mesajlar: imza, gönderme geri alma süresi, sohbeti sessize alma (Textra ayarları)
- 0.10.309: derlenmedi — müzik çalar ve söz ekranı Inter yazı tipi (kalın gerçekten kalın, Türkçe/Kiril/Yunan harfleri); bozuk etiketler (CanlÄ± gibi) düzeltilir
- 0.10.310: derlenmedi — müzik: ses 0 olunca durur açılınca devam eder, medya bildirimi tam oynatıcıyı açar, söz bildirimi söz ekranını açar (Material), bozuk LRC (saniye gösteren satırlar) süzülür, türe göre otomatik ekolayzır, Türkçe karakter duyarsız arama; klavye: aşağı kaydırma ile kapatma düzeltildi, 3 nokta menüsünden kes/kopyala/yapıştır kalktı, Sesle yazma + Çeviri + Pano eklendi; Dosyalar ana sayfası turuncu yerine tema vurgusu; Mesajlar SmsManager eski Android uyumu
- 0.10.311: derlenmedi — Buds yöneticisi yeniden yazıldı: çizili kulaklık+kutu, pil çubukları, ayarlar kulaklığın uzun durum mesajından okunur (EQ, dokunma, kilit, ANC gücü, ortam sesi, kesintisiz bağlantı...); tüm uygulama NewApi taraması için elle çalışan lint iş akışı
- 0.10.311: tüm uygulama NewApi taraması: tek bulgu (Html.fromHtml, API 24) HtmlCompat ile düzeltildi
- 0.10.312: derlenmedi — Dosyalar ana sayfasından zip/apk açınca dış uygulamaya gitmez, Gözat sekmesi o klasörde açılır ve arşiv içinde gösterilir
- 0.10.313: derlenmedi — hızlı ayarlar: kutucuk animasyonu art arda basılınca kare resmini asıl simge sanıyordu (DNS simgesi bozuluyordu), düzeltildi
- 0.10.314: derlenmedi — Buds: bildirim yalnız ekran açıkken ya da "arka planda bağlı kal" açıkken; kulaklık yokken servis kapanır, bağlanınca (ACL) yeniden açılır
- 0.10.315: derlenmedi (derleme istenmedi) — Windows 11 başlat menüsünde Oturumu kapat altta sabit; Pulse ana sayfada geri tuşu uygulamayı kapatmaz, arka plana atar
- 0.10.316: derlenmedi (derleme istenmedi) — klavye: boş alanda ve cümle başında başlangıç önerileri (I/The/Hello, Merhaba/Ben/Evet), e-posta alanında önce yazılan adresler sonra alan adları, silme tuşu basılı tutulunca kelime kelime siler
- 0.10.317: derlenmedi (derleme istenmedi) — klavye: yazarken kaydırma (flow) mavi çizgisi kaldırıldı, aşağı kaydırınca klavye kayarak değil hemen kapanır (siyah ekran sorunu)
- 0.10.318: derlenmedi (derleme istenmedi) — klavye otomatik düzeltme: Türkçe çekimli kelimeler (taban + ek) tanınır ve düzeltilir, uzun kelimelerde daha çok hataya izin, dokunduğunuz öneriden kendi yazım hatalarınızı öğrenir
- 0.10.319: derlenmedi (derleme istenmedi) — klavye: kendi kelimelerini telefondan öğrenir (gönderilen SMS, kişi adları, telefon sözlüğü), küçük sözlük bir kez internetten tam listeyle (150 bin, çekimli biçimler) değişir, öneriler önceki kelimeye göre sıralanır
- 0.10.320: derlenmedi (derleme istenmedi) — klavye: benzerlik oranı (varsayılan %90) ayarı; bilinen kelimeye bu kadar benzeyen kelime kendiliğinden düzeltilir
- 0.10.321: derlenmedi (derleme istenmedi) — klavye: öneriye basılı tutunca "Bir daha önerme" (kelime yasaklanır), titreşim gücü kaydırıcısı (yumuşak varsayılan)
- 0.10.322: derlenmedi (derleme istenmedi) — klavye: parola yöneticisi (otomatik doldurma) önerileri klavyenin öneri çubuğunda çıkar (Android 11+, satır içi öneri), kullanıcı adı/parola/kart seçilebilir
- 0.10.323: derlenmedi (derleme istenmedi) — klavye: emoji paneli yeniden yazıldı (tüm Unicode emoji + bayraklar, 9 grup, kaomoji sekmesi, Türkçe/İngilizce arama; cihazın çizemediği emoji gizlenir), Boyutlandır ekranı artık pencereyi tüm ekrana yaymaz, kelime içindeki virgül hata sayılır (ayarlanabilir)
- 0.10.324: derlenmedi (derleme istenmedi) — klavye: "kelime içindeki virgül hatadır" varsayılan KAPALI (Bak,şey gibi virgülden sonra boşluksuz yazımı bozuyordu)
- 0.10.325: derlenmedi (derleme istenmedi) — klavye düzeltme: dokunma modeli (parmağın tuşa değdiği nokta ve kişisel kayma, komşu tuş maliyeti buna göre), iki kelimelik bağlam (üçlü kelime dizisi) ile sıralama
- 0.10.326: derlenmedi (derleme istenmedi) — klavye: Ctrl+A/C/X/V/Z/Y düzenleyicinin kendi eylemleriyle (a yazma sorunu), virgül tuşunun üstünde mikrofon, pano yeniden yazıldı (her şeyi kaydeder, resim, sabitleme, toplu silme, 1 saat-3 gün saklama); Dosyalar alt çubuğunda Analiz yerine 3 nokta menüsü (Medya araçları, dosya dönüştürücü, boyutlar, türler, önbellek, ayarlar)
- 0.10.327: derlenmedi — Dosyalar ana sayfası: bir türe (Resimler, Videolar...) tek dokunuşla doğrudan listesi açılır; tür sayfasının metinleri Türkçeleştirildi
- 0.10.328: derlenmedi — Widget Stack kaplama widgetlarında tuş alanları 7 px genişletildi; geniş ekranlarda (tablet, yatay) sayfalar 900dp genişlikte ortada
- 0.10.329: derlenmedi — Dosyalar alt çubuğu gezinme çubuğu boşluğu ve sekme geçişinde beyaz parlama giderildi; launcher ana ekranına uygulama / dosya / resim ekleme ve yeni klasör; otomatik düzeltme tek hatayla sınırlandı (rastgele 2 karakter değişimi)
- 0.10.330: derlenmedi (derleme istenmedi) — Medya araçları Ekstra uygulamalardan çıktı (Dosyalar > Diğer içinde); "Mesajlarımdan öğren" kaldırıldı; son kopyalanan metin öneri çubuğunun sağında 2 kez yapıştırılabilir; otomatik düzeltme en çok 1 hata, en az 3 harf, uzunluk farkı en çok 1; d↔s karışıklığı ucuz; silme tuşu basılı tutulunca hemen kelime kelime siler
- 0.10.331: derlenmedi (derleme istenmedi) — müzik bildirimi doğrudan müzik penceresini açar (aracı etkinlik yok); klavye ayarlarında canlı klavye varsayılan kapalı (üstteki düğmeyle açılır); klavye verisi içe/dışa aktar (zip: ayarlar, öğrenilen kelimeler, hatalar, pano)
- 0.10.332: derlenmedi (derleme istenmedi) — hesap makinesi Windows temaları: tuş yüksekliği 46 -> 58 dp; klavye sembol sayfası 1: € yerine /, ona (uzun basınca ‰ € $ £ ¥)
- 0.10.333: derlenmedi (derleme istenmedi) — Poweramp yedekleri telefonda aranıp listelenir (seçici görmezse); Dosyalar: "Gözat sayfasında aç" ayarı; silme tuşu basılı tutulunca hızlanır; otomatik düzeltme yalnız boşluk tuşuyla (nokta, virgül vb. düzeltmez)
- 0.10.334: derlenmedi (derleme istenmedi) — klavye: sayılara basılı tutunca üst simge/sembol/kesir, parlaklık (tümü + boşluk satırı ayrı), hareketli RGB ışık (klavye çevresi ya da her tuş), klavye içinde çeviri (MyMemory, siteye atmaz); Medya araçları: resim kalitesi kaydırıcısı + önizleme; Dosyalar Diğer menüsünde tek Medya araçları ve dönüştürücü satırı
- 0.10.335: derlenmedi — Mesajlar: otomatik spam engelleme (rehberde olmayan numaradan reklam/bahis/kredi + link; kod ve hesap mesajları hariç), ayarlardan kapatılır; klavye RGB animasyon içe aktarma düzeltmesi
- 0.10.336: klavye: "öneri istemiyorum" bayrağı olan alanlarda (Chrome web alanları) da öneri çubuğu açık, yalnız otomatik düzeltme kapalı
- 0.10.337: klavye: emoji paneli tuşlarla aynı boyda (küçülmez), kategori simgeleri ikon, arama çubuğu sekmelerin yerine; otomatik doldurma önerileri siyah zemin beyaz yazı
- 0.10.338: launcher: ses efektleri medya sesinde çalar, uygulama açılınca da tık sesi
- 0.10.339 klavye ek tus satiri: Shift varsayilan, Tab/Shift ikonlu, Win logosu (derlenmedi)
- 0.10.340 klavye: ek tus ikonlari (opsiyonel), Win+D/L/A/N ve Alt+Tab, emoji sayfalari kaydirma, gri vurgu, ust menude Extra, geri-tusu duzeltme hatasi, silerek yazim hatasi ogrenme; launcher New menusu (derlenmedi)
- 0.10.341 klavye ayarlari: Yazim testi (kelime goster, yanlislari ogren) (derlenmedi)
- 0.10.342 klavye: cumle basi buyuk harf alani isaretlemese de (derlenmedi)
- 0.10.343 klavye: sayisal alanlarda ozel numpad, isim alaninda onceki isimler (derlenmedi)
- 0.10.344 klavye: asagi kaydirip kapatirken alttaki tus yazmasin; alt satirda sistem hareket bolgesi hari (derlenmedi)
- 0.10.345 Poweramp eslesme yedegi, silerken titresim, ok satiri tek, numpad * #, yazim testi duzeltme kapali, SMS gonder/ara ikonlari (derlenmedi)
- 0.10.346 SMS liste 3 nokta + ikonlu pin/sessiz/engel, Win11 yatay mavi duvar kagidi, medya araclari ikon kutulari (derlenmedi)
- 0.10.347 klavye: eki dogru ama govdesinde harf eksik kelimeler (ikisindayim) duzelir (derlenmedi)
- 0.10.348 klavye benzerlik orani 80 (derlenmedi)
- 0.10.349 Poweramp yedek puan olcegi (10/100 -> 5 yildiz) ice ve disa, mix kaynagi En yuksek puanli (derlenmedi)
- 0.10.350 klavye: kopya 2 dk, panoda ikon, engel ikonu, TYPE_NULL alanlarda oneri; muzik Favoriler yildiz sayisi + siralama; Dosyalar ev ikonu ve geri->ana (derlenmedi)
- 0.10.351 ekran kapagi: iki kisa dokunus kapatir (derlenmedi)
- 1.0 (versionCode 400): Buds harekete gore gurultu denetimi, surum notlari, Welcome yenilikler, guncelleme dogru APK (derlenmedi)
- 1.0.1 hava durumu: arka plansiz Samsung tarzi widget (sehir degistirmek icin dokun) (derlenmedi)
- 1.0.2 klavye emoji sayfasi (ustte arama, altta kategori cubugu) ve sembol sayfasi Samsung duzeni (derlenmedi)
- 1.0.3 klavye: geri alinan duzeltme bir daha yapilmaz, 2 kez geri alinirsa kelime sizin kelimeniz (derlenmedi)
- 1.0.4 ozel kelimeler sozlukte baslangicta (derlenmedi sonra derlendi)
- 1.0.5 klavye ayarlari kategorili/ikonlu, sadece TR+EN sozluk, adres cubugunda arama duzeltmesi, bosluk unutulan kelimeleri ayirma; Poweramp sirala yon + yildiz beyaz (derlenmedi)
- 1.0.6 klavye emoji sayfasi: sus sembolleri sekmesi (gruplu) (derlenmedi)
- 1.0.7 arac penceresi cift acilis: ikinci pencere gorev olarak kaldirilir (derlenmedi)
- 1.0.8 hesap makinesi: tus basma animasyonu, satirlar ust uste binmesin (derlenmedi)
- 1.0.9 klavye sembol sekmesi: Unicode sembol bloklari (3485 isaret) ailelere ayrildi, Unicode adiyla aranir; cicek listesi kalkti (derlenmedi)
- 1.0.10 sembol sekmesi: ciceker, kalp, yildiz, sus, ay, cizgi, parantez once; Unicode bloklari sonda (derlenmedi)
- 1.0.11 sembol sekmesi: emoji cikarildi, yalniz Unicode metin isaretleri (derlenmedi)
- 1.0.12 sembol sekmesi: Hazir suslar (desenler) kategorisi en basta (derlenmedi)
- 1.0.13 sembol sekmesi: kategori adi Presetler (derlenmedi)
- 1.0.14 Presetler: 140 karisik desen daha (derlenmedi)
- 1.0.15 Presetler: rastgele degil, simetrik tasarim (orta isaret + iki yanda aynali kanat/cizgi/parantez) (derlenmedi)
- 1.0.16 sembol sekmesi: aile ikonlari, alt kategoriler (Presetler: Klasik/Cizgi/Kanat/Parantez/Uzun; Unicode bloklari), uzun presetler (derlenmedi)
- 1.0.17 Dosyalar tur listesi: onizleme, uzun basinca secim, toplu silme; launcher yatayda sutunlar genislige gore (derlenmedi sonra derlendi)
- 1.0.19 klavye istatistik + isi haritasi, launcher Windows fare imleci, Dosyalar Diger sayfasi (derlenmedi sonra derlendi)
- 1.0.21 klavye: UD agac bankalarindan kelime cifti modeli (tr 25k, en 21k), bolunmus tuslar; launcher uygulama acma animasyonu; hava durumu sehir cipleri (derlenmedi sonra derlendi)
- 1.0.22 klavye tus gorsel takili kalma duzeltmesi (shift degisince dokunus dusmesin), minimal tablet modu (UI %20 buyuk) (derlenmedi)
- 1.0.23 klavye WhatsApp sohbetinden ogrenme; Save to Material + MediaStore yedegi; ekran kapagi sarjda otomatik acilma (kenar tetik, gercek donus); pil olculen kapasite (AccuBattery gibi) (derlenmedi)
- 1.0.24 gelismis kapak: gece kirmizi modu varsayilan kapali (derlenmedi)
- 1.0.25 oto duzeltme: konusma ekleri (yapiyom, geliyon, yapicam, gitcem, gormicem) bilinen; uzatilmis kelimeler (buuu, tamamdirrr) duzeltilmez (derlenmedi)
- 1.0.26 oto duzeltme: uyumicsm tarzi konusma olumsuzlari duzeltilir (derlenmedi)
- 1.0.27 oto duzeltme: es yakin eklerde govdenin unlusune uyan ek secilir (uyumicsm -> uyumicam) (derlenmedi)
- 1.0.28 WhatsApp ogrenme: sik tekrarlanan yazim hatalari kelime sayilmaz, yazim hatasi olarak ogrenilir (derlenmedi)
- 1.0.29 oto duzeltme: eşik .8 + yaygin kelime (yanlis duzeltme %60 az, kendi kelimeni bozma 105->18); lyrics kaydirinca kapanmaz, fontu buyuk/kalin; seek cubugu esnek (derlenmedi sonra derlendi)
- 1.0.30 acilis sesi secenegi (varsayilan kapali), README kategori listesi (derlenmedi sonra derlendi)
- 1.0.31 WhatsApp ogrenme kaldirildi; oto duzeltme esigi base>=7; silip yazinca ogrenme (benzer kelime); Key set paneli (F tuslari, Shift...) + Arrows/Extra keys ayri; isi haritasi yukleme; ayar bolumleri arasi bosluk; Sozlukler bolumu kaldirildi; Train autocorrect; yuzen klavye; SMS kisayollari simgede gorunmez; Dosyalar seffaflik ayari (derlenmedi, bekle) 
- 1.0.32 soz ekrani: ustteyken bir daha asagi cekince yalniz sozler kapanir, oynatici kalir (derlenmedi)
- 1.0.33 launcher Windows 11: gorev cubugu ve menu seffafligi ayari (derlenmedi)
- 1.0.34 klavye: ceviri (MyMemory + Lingva, panodan ceviri, hata mesaji), sembol: Civi yazisi + Hiyeroglif aileleri, alisilmis yazim hatalari listesi (derlenmedi, bekle)
- 1.0.35 Toolkit: Floating keys (yuzen dugme: dokun=klavye, basili tut=mini panel Esc/ok/F, yana kaydir=sayfa); alisilmis yazim hatalari listesi 131 cift (derlenmedi, bekle)

- 1.0.36 Shift key: outlined arrow / arrow+underline (one capital) / filled arrow joined to underline (caps lock) - derlenmedi, bekle
- 1.0.37 Key pop-ups (long-press alternates, key preview) no longer cut off on the number row - derlenmedi, bekle
- 1.0.38 Recent emojis keep their places while the emoji page is open - derlenmedi, bekle
- 1.0.39 Symbol families on top (above the search), presets one per row and fully visible; deleting the space behind a word reopens it with suggestions - derlenmedi, bekle
- 1.0.40 Keyboard: optional "Extra button" (compact bottom row + Shift at left, off), top bar brightness slider - derlenmedi, bekle
- 1.0.41 Files: the three-dot "Open archive" opens the full-page archive view (copy/cut/delete on selection), not the pop-up - derlenmedi, bekle
- 1.0.42 Files: "Open as" dialog with tonal rows and a Cancel text button; File types list: trash button replaced by swipe actions (left/right: delete, open, select, none; default delete) - derlenmedi, bekle
- 1.0.43 Material dialogs: action buttons are plain text buttons, Material PButton is tonal instead of outlined - derlenmedi, bekle
- 1.0.44 Keyboard: translation bar (languages at the ends, swap between, live translation, tap to insert); typo learning needs 3 sightings for real words and forgets slips that were undone - derlenmedi, bekle
- 1.0.45 Keyboard: many more accented letters on long press; element symbols (He, Fe, Cu...) on capital letters, switch in settings - derlenmedi, bekle
- 1.0.46 Keyboard effects (like Samsung Keys Cafe): keyboard colour wash (6), key colour effect (4), key motion (3), touch sounds (5 synthesised) - derlenmedi, bekle
- 1.0.47 Material everywhere (1/3): 12 direct AlertDialogs now go through WinAlert (Poweramp track sheet, Folder tree, Contacts, Excel, Calendar); calendar event detail keeps its own (icon slot) - derlenmedi, bekle
- 1.0.48 APKs hold arm64-v8a, armeabi-v7a and x86_64; README and release notes brought up to date - derlenmedi, bekle
- 1.0.49 Install window: Material pill buttons, switches instead of On/Off, removed Install on open and Set source to Play Store, option to remove unused CPU libraries - derlenmedi, bekle
- 1.0.50 Element symbols removed from the long press (two letters can be typed) - derlenmedi, bekle
- 1.0.51 Emoji categories on top, presets in compact flow (2-3 per line) with 30 more, emoji removed from the symbol tab - derlenmedi, bekle
- 1.0.52 Emoji pager no longer stops half way; an empty text box always starts with a capital - derlenmedi, bekle
- 1.0.53 Files home opens with the last numbers at once (cached), refreshes behind - derlenmedi, bekle
- 1.0.54 Keyboard: swapped letters (wrok) always mended, clipboard long press (pin, shortcut, select) with shortcut names offered as suggestions, panels keep the keys height - derlenmedi, bekle
- 1.0.55 Files archive row menus (open/cut/copy/paste/delete); Poweramp album_art/artist_art folder import and artist pictures - derlenmedi
- 1.0.56 Files: share several picked files at once; fix verticalScroll import
- 1.0.57 Files: Share in the selection three-dot menu (one or several files) - derlenmedi, bekle
- 1.0.58 stars as icons + grid views, folder copy from archive as folder, short extract-to name and tighter menus, clipboard icons, teach a correction, floating keys QS tile, Files mini audio player with speed - derlenmedi
- 1.0.59 Messages: new message shows contact suggestions; imported Poweramp backups are kept inside Pulse - derlenmedi
- 1.0.60 Poweramp pictures: albums matched by album name too, unmatched names and the error shown - derlenmedi
- 1.0.61 poweramp-backup opens as archive; space after word.next typed together (not addresses); saved e-mails offered by first letters; Save-to dialog and chips in Material 3 - derlenmedi
- 1.0.62 Files: archive entry Info, extension toggle button, HTML shown in the app (also extension-less pages), Open as always asked for unknown types - derlenmedi
- 1.0.63 Emoji page: the suggestion bar holds back + categories; emoji suggestions from every emoji keyword, also for the word just written - derlenmedi
- 1.0.64 Presets shrink to the keyboard width so long ones show whole - derlenmedi
- 1.0.65 compile fixes (grid itemsIndexed, keyboard icons)
- 1.0.69 Weather: sky/amoled/app page looks, list of places, swipe between places; Install window uses the Material accent
- 1.0.70 Weather: pollen, today vs yesterday, UV, humidity, wind compass, dew point, pressure, visibility cards; 48 hours; 30-minute background refresh
- 1.0.72 Files: Recordings/Call page (people tiles from contacts, calls by day, play in the Files player)
- 1.0.73 Palette extra app (icon pack studio from the own Palette project: palette, style, preview, provider, PNG zip)
- 1.0.75 Pulse launcher: built-in icon packs (Pulse Palette, Material glass, theme colour only); Palette: use in the launcher
- 1.0.77 Windows 11/10/7/XP/95 icon looks as built-in packs and Palette styles
- 1.0.78 Motion cues rebuilt on the sensor fusion of Motion cues (GPL-3.0); keyboard translation input
- 1.0.79 Keyboard lower in landscape
- 1.0.80 Keyboard: a typed word is never replaced or dropped when the app moves the cursor; Messages: new-message button at the bottom
- 1.0.81 APK info dialog in detail + Save APKs, split zip parts joined, bundles in a folder, Save as name field Material, autocorrect strength + context + keep-case, emoji mix
- 1.0.82 Speed test Start button: transparent, outlined in the accent colour
- 1.0.83 Keyboard: a learned slip never turns a real word into a rarer one; real-word pairs need 5 sightings (derlenmedi)
- 1.0.84 Weather: settings page (colours, unit, source, alerts), top bar cleared, hours/days first, 96 h + 16 days, MGM source, moon image, richer clouds, wind compass (derlenmedi)
- 1.0.85 Keyboard: the key beside ?123 is the three-dot menu (emoji / none selectable)
- 1.0.86 Keyboard: correction and space/punctuation as one edit (no flicker of the word)
- 1.0.87 Installer: one LSPosed button, opens Vector (not patch tools); player widgets open the now-playing screen on a tap (derlenmedi)
- 1.0.88 Weather clear widget: aligned, smaller temp, pin icon, refresh in background; icon pack list with previews and groups; Emoji mix full page; ?123 key shorter (derlenmedi)
- 1.0.89 Keyboard: the corrected word and the space/sign in one commit (no word-gone gap); call recordings: chosen person kept (derlenmedi)
- 1.0.90 Messages: no three-dot button on thread rows (the menu is on long press) (derlenmedi)
- 1.0.91 BlackOut extra app (engine, LSPosed/Vector module entry, colour bar, dark pages, overlays; steps 1-8; import from the stand-alone app) (derlenmedi)
- 1.0.92 Lyrics: share card (pick lines, colours from the cover), menu with share / delete / search again, pull down to close; autocorrect only to the word the bar shows (derlenmedi)
- 1.0.93 Lyrics for the whole library in the background (worker, setting, Search now); Search and apply missing covers with a hand-pick list for the ones not matched
- 1.0.94 Darker minimum: keyboard brightness sliders go down to 5% and the veil reaches black; screen cover brightness down to 1% (derlenmedi)
- 1.0.95 Keyboard: Shift while a word is open puts the capital-letter form in the middle of the bar; a tap applies it (derlenmedi)
- 1.0.96 README screenshots in one collapsed block, small and framed; keyboard: capital letter worked out again when the field is emptied or the cursor jumps (Beeper); emoji mix bigger (derlenmedi)
- 1.0.97 Emoji mix: circles + result you tap to send; clear weather widget closer to the left; weather: 24 hours from several forecasts combined (derlenmedi)
- 1.0.98 Motion cues: a strip along the top edge; double tap closes, swipe down closes and opens quick settings
- 1.0.99 Keyboard: emoji key back beside ?123, the bar holds the three-dot menu instead; Backspace after a tap inside a word deletes at the cursor; lyrics share is one card (5 cover colours, black, white) with a share button; Canvas (a video per song) in the song menu; long press on the lyrics button asks to delete; calculator Back and x² input; weather add-a-place page and no source note; calendar month styles (round, no frames); BlackOut setup list, app preview, Fluent icons; Palette page redone (derlenmedi)
- 1.0.100 Palette: the full app ported (Studio, Preview with home screen / wallpaper / widgets, Apps rules, Apply, Settings with automation), widgets, live wallpaper, saved looks and palettes, undo (derlenmedi)
- 1.0.101 BlackOut: every page of the BlackOut app ported (Home, Apps with a page per app, Colours, Settings, setup guide, Dark pages, force dark, LSPosed module, Material You greys, Samsung Notes); Palette link fix (derlenmedi)
- 1.0.102 Music player no longer brings Files forward; Files: swipe actions moved to settings, back from a type list goes home, search at the top right and closes on leaving, zip rows lose cut/copy and show sizes; keyboard: suggestions in two passes (fast), word deleted whole and retyped is learned (derlenmedi)
- 1.0.103 Island: a pill over the camera (music, notifications, charger, headset, volume, torch, removed apps, screenshots), own page with permissions, position and what to show (derlenmedi)
- 1.0.104 Files: an archive opened from another app shows in the browser; several APKs selected get an install button (Multi install with stacked icons); keyboard: a screenshot just taken waits in the right slot to be pasted (derlenmedi)
- 1.0.105 Files data (recents, ex_* options, tree view) in its own multi-process file; own preference files re-read when another process changed them (theme of Pulse no longer overwritten by Files) (derlenmedi)
- 1.0.106 Keyboard: long-press an emoji for skin tones (not in Recents); Emoji Kitchen "Blend" style draws a face from each emoji's key points (eyes, brows, mouth, extras) on a default template, no image files; mixed pictures keep only the last 8; Data section shows what the keyboard stores with delete buttons (derlenmedi)
- 1.0.107 Notes: top-right of the list cleaned to New note + one Settings button; sort, grid/list switch, trash and the look chooser live in Settings (derlenmedi)
- 1.0.108 Notes: New note opens a blank note at once (templates moved to Settings), Select all in selection mode, sketch bar follows the look (Paint grey only in Windows looks) (derlenmedi)
- 1.0.109 Keyboard: a key (the Enter/OK key after Send, Done, Search) no longer stays shown pressed (whitish) when the touch ends by the keyboard closing or an action throwing: reset in finally (derlenmedi)
- 1.0.110 Keyboard: the arrow keys under the letters no longer stay lit (whitish) after a touch is cancelled; reset in finally (derlenmedi)
- 1.0.111 Keyboard: holding backspace deletes one letter per repeat (faster the longer it is held), no more whole-word deletes (derlenmedi)
- 1.0.112 Automatic daily backup (AutoBackup.kt): Pulse zip and a separate keyboard zip in Download/Pulse backup, last 7 each, switch + Back up now in Settings > Data; the backup now also carries all notes with attachments, clipboard, playlists extras; the coloured Win95/XP/7 icons are left out of the top-right menu (derlenmedi)
- 1.0.113 Backup: everything under files/ and every shared_prefs file goes in (denylist of rebuildable caches, secrets stay out) instead of a short allowlist (derlenmedi)
- 1.0.114 Emoji Kitchen removed entirely; keyboard bar: three-dot button pinned at the right with the Fluent icon; Weather: MGM hours are a voice in the combined hours, the settings text and a line under the hours say how many forecasts, UA header + the real error text + Geocoder fallback for city search; Notes sketch: page in the look colour with a fade-in (saved on that colour); Lyrics share: cover photo as a card choice with veil and dark/white writing (derlenmedi)
- 1.0.115 Music: Search and apply missing covers runs in a foreground service with a progress notification and a progress bar on the page, and goes on when the page is left (CoverRepairService); Weather: the forecast request is retried and falls back to a smaller request (derlenmedi)
- 1.0.116 Launcher (Windows 11): a turned screen swaps a wallpaper for its partner (phone <-> wide, 11 pairs), the default blue bloom now has its wide picture (win11_wall_17, also pack35 in the backdrop picker) (derlenmedi)
- 1.0.117 Floating keys button: Material keyboard icon instead of the text glyph; a tap focuses and clicks the focused field, or the first editable field on screen, with haptic feedback and a message when nothing is found (derlenmedi)
- 1.0.118 Weather: two more sources (MET Norway, OpenWeatherMap with your key), converted to the Open-Meteo shape (WeatherProviders.kt); Island: tighter opened card (smaller cover that stays inside the curve, percentage and moving bars, shorter heights), default pill 126x36 (derlenmedi)
- 1.0.119 BlackOut All colours: long press a colour to select several (tap adds more), one tap on a colour turns all of them at once; the list no longer scans by itself when the card opens (button only, scan kept for the session) (derlenmedi)
- 1.0.120 Keyboard: the screenshot lookup no longer fails on Android 11+ (LIMIT in the sort order), arrows move the cursor through the field instead of key events (no navigation highlight); DNS tile icon redrawn; Notes: tools of a note behind one plus button; every extra tool returns Pulse look on stop (derlenmedi)
- 1.0.121 Install window: Material buttons are rounded squares (14 dp, not pills) with more room between them; Files: less space between file rows (derlenmedi)
- 1.0.122 Keyboard: names written with a capital behind another word (Umut, Kıvanç, Şahin) are remembered with their capital (kbd_forms.txt, in the keyboard backup) and the next-word suggestions and completions give them back; the word-pair learning already there then offers Kıvanç after Umut (derlenmedi)
- 1.0.123 Files: videos open in the Pulse video player at once (Open as holds Video player, Gallery); keyboard: holding backspace deletes word by word again with a tick each time, the keyboard comes back as plain keys after it was closed on the emoji page; BlackOut: module-status message no longer talks about a separate BlackOut app and explains the steps, the settings file is created on first open; covers: Write the saved covers into the songs (mp3) with a rehearsal on a copy, a backup of every file and a check that undoes a bad result (derlenmedi)
- 1.0.124 Weather: five days first (a link opens all 16), the hour it is now is lit in the next-hours row; keyboard: a full stop behind a known word (and ! ?) adds the space and a capital with the next letter (derlenmedi)
- 1.0.125 Weather: temperature and rain charts show the numbers (high and low on the chart, now / high / low with times, highest chance and average of rain); keyboard: after an automatic correction the typed word stands quoted in the bar (tap puts it back), a long press on the quoted word saves it as a right word (derlenmedi)
- 1.0.126 Covers: Deezer and MusicBrainz / Cover Art Archive are searched besides iTunes (more finds, duplicates merged, the first picture that really downloads is taken); the cover picker opens a picture large first, with Apply and Back (derlenmedi)
- 1.0.127 Keyboard: the undo-on-backspace is gone (code and setting); a word taken away with the held delete key and replaced by an alike word is learned as a slip; learned slips show in the bar from the first (fast) pass; softer tick while the held delete repeats; 30 slips from the chat added to the built-in slip list (derlenmedi)
- 1.0.128 Voice key: the long press switches to the voice keyboard after the finger is up (it closed and needed a second tap); screenshot slot shows only the picture; music: a song with a Canvas shows it as the cover with one small play button; cover search tries album, song+artist, song alone and takes the first picture that downloads; the run lists what it found (change) and what it did not (derlenmedi)
- 1.0.129 Good Lock modules manager (extras/goodlock, Extra apps, Material by default, own look picker): installed modules by name, version, update check from APKMirror feed, app info / open / Galaxy Store / APKMirror links; written from the idea of the BadLock README, no code of it used (derlenmedi)
- 1.0.130 Files: an archive opened from another app goes the way a tap on the home page goes (browser tab + pending file), so its contents show at once instead of the home page (derlenmedi)
- 1.0.131 Cover search: what the strict pass did not find gets a second pass that takes the first results of the search; songs still without a cover open the search (picker) one after the other (derlenmedi)
- 1.0.132 Good Lock: the module pictures, no Galaxy Store link (Update and the mirror button open APKMirror), volume down/up switches the tabs; keyboard: holding delete takes a whole word at once, no single letter first (derlenmedi)
- 1.0.133 Share lyrics: the cover reaches the share card in the Material player and the normal Poweramp view too (cover on the left), and the colours are read from a software copy of the cover (a hardware bitmap gave the blue-green fallback) (derlenmedi)
- 1.0.134 BlackOut app page: the setup walkthrough has no card around it, the info card is at the bottom and collapsed, the colour list shows a few colours when collapsed (derlenmedi)
- 1.0.135 Files search: typing searches the folder and everything under it by itself, a globe button beside the field searches the whole storage (derlenmedi)
- 1.0.136 Files: the Material delete question and the APK info card are Material cards, an archive copy is pasted once (derlenmedi)
- 1.0.137 Call recordings: closer tiles, contact photos (asks the contacts permission), an Everyone chip; Files: Browse pressed again goes back to the top folder (derlenmedi)
- 1.0.138 Keyboard: the password manager chips sit close together on grey rounded backs, one alone in the middle, arrows at the ends to scroll (derlenmedi)
- 1.0.139 Keyboard: the key bar (was Arrows) has Fn with F1-F12, Shift, Home, End, Page up/down, Esc, Tab; 60 more cute presets and the presets tab beside the smiley, kaomoji between animals and food; clipboard rows roomier with swipe right = delete, swipe left = shortcut, keep time 1 h / 3 h / 1 day / 1 week / never (default); Files: the paste offer goes after one paste (derlenmedi)
- 1.0.140 Poweramp player: the elapsed and total time sit on dark pills at the two ends of the wave; the track menu is darker and delete asks the system when the file is not the app's own; keyboard: a second extra key row (derlenmedi)
- 1.0.141 Clipboard keeps a week by default; the keyboard menu has settings and themes beside the back arrow; Poweramp times at the two edges (derlenmedi)
- 1.0.142 Network usage: Wi-Fi and mobile data kept apart (Both / Wi-Fi / Mobile switch), a 30-day strip, one page per day to swipe with the hourly graph on top and the apps of the day below (derlenmedi)
- 1.0.143 Installer: the silent install from the install window goes through a session fed from a shared folder, so Shizuku can read the file ("Unable to open file /data/data/..." fixed) (derlenmedi)
- 1.0.144 Keyboard: swipe-down to close only on the plain keys screen (was closing on emoji/clip scroll), key bar arrows removed (duplicated the bottom row) and a quick toggle beside voice, lyrics notification keeps the last line through a gap; floating keys panel opens when no text field is found; Good Lock and Buds manager get a Win11 Fluent icon; Files: an archive opened from outside switches to Browse at once (no Home flash), back from Folder sizes/File types/Clean cache always returns to Browse (derlenmedi)
- 1.0.145 Art Craft: a release with two APK builds of the same app no longer lists it twice (the larger build is kept) (derlenmedi)
- 1.0.146 Keyboard: a Key layout picker (Numbers, Game controls with a WASD cluster and action keys) from the three-dot menu; Buds manager: a pulsing accent and gradient battery bars; Extra apps store lists every tool (Toolkit, Office, Download manager, App lock, Health, APK installer, Authenticator, Good Lock, Network usage were missing); keyboard quick-paste slot shows a copy of any length, not only under 400 characters (derlendi)
- 1.0.147 Motion cues no longer stops when Pulse is swiped away from recents; the globe icon (Network type, DNS and TTL, Weather, language pickers, ...) no longer falls back to a plain Info icon in Material/AMOLED; Extra apps store: every built-in app and tool gets its own switch for being listed in the ⋮ menu, separate from Settings > Extra apps (also fixed a "download" id mismatch that kept the Download manager's switch from doing anything) (derlendi)
- 1.0.148 Keyboard: the autofill (password manager) chips in the top bar no longer flicker — background gone, chip text stuck in place — while dragging the bar to close the keyboard; their background is a darker grey (derlendi)
- 1.0.149 Quick Settings: a tile's tap-pop animation (DNS and every other Toolkit tile) no longer leaves the icon stuck on a squeezed in-between frame when a second tap lands before the first pop finished — the restore now runs in a finally, so it survives being cancelled mid-animation (derlendi)
- 1.0.150 Weather: "my location" now also tries GPS when the network provider has nothing (it used to just say no location fix, or fall back to a stale last-known fix in the wrong place — the wrong sunrise/sunset time came from that); BlackOut: turning on "Apply in the ticked apps" while the grey threshold and tint are still at their plain defaults also picks a threshold that catches AMOLED surfaces well and a blue-black tint, instead of flat unconfigured black (derlendi)
- 1.0.151 Cache cleaner: finds any folder named "cache" anywhere under an app's data, not only a fixed list of exact paths (that was why some apps' cache, e.g. YouTube's, never showed up); floating keys: a full QWERTY letter page (not only Esc/Tab/arrows/F-keys/Home-End); fixed the panel never actually moving when dragged by its bar (it was updating the wrong view's layout, the call silently failed every time) (derlendi)
- 1.0.152 Office: the Windows 7 look (2013) had no dark sibling at all, unlike 365 and 2017 — Pulse's dark mode had no effect on Office's chrome when that was the active look. Added "Office 2013 dark" the same way the other two have one (derlendi)
- 1.0.153 Files (embedded tab): a list/grid toggle in the action bar, next to the extension toggle; a folder's own size now shows there too (it was already measured in the background, just never shown outside the standalone Files app) (derlendi)
- 1.0.154 Network type: pressing a mode said "Done" even when the phone's radio never actually changed — the check only proved the legacy setting we wrote ourselves stuck (always true with root), not that the modem took it. Now checks the allowed-network-types readback for the mask actually asked for, so a real failure shows as one instead of a false "Done" (derlendi)
- 1.0.155 New extra app: Browser. The phone's own WebView, not a bundled Chromium (that is why a real browser APK is 150-250 MB and this one barely grows): dark mode (algorithmic darkening / force-dark), a short built-in ad/tracker block list, bookmarks, a settable home page. No Chrome extension support — that API is not exposed to a plain WebView at any size (derlendi)
- 1.0.156 Browser: a page's own fullscreen video (the expand button on an embedded player) now actually works — a plain AMOLED black background behind it, system bars hidden, back exits fullscreen first. Ad blocking was already on by default (derlendi)
- 1.0.157 Browser: opens AMOLED black by default like the media players; the ad/tracker block list grew from ~50 to 313 domains (ranked by how often each appears in a well-known community hosts list, with the few that double as ordinary site/OEM hosting left out so real sites do not break); a "run a script on every page" option (plain JavaScript, not a full userscript manager). Download manager: a link with no download link found on its page now offers "Open in Browser instead" (derlendi)
- 1.0.158 Launcher: sounds never played on "Material" (the default launcher look) or AMOLED/One UI/ColorOS — only the Windows looks (95/XP/7/10/11) had a click/startup sound mapped at all, so most phones were silent by default regardless of the sounds setting. Those four now borrow the Win11 (Fluent) sound set until they get ones of their own (derlendi)
- 1.0.159 Size: the debloat list (assets/debloat/uad_lists.json) was pretty-printed JSON shipped as-is, minified in place (-228 KB, parses identically); four ambient white-noise loops (steam/heartbeat/vinyl/traffic) were uncompressed WAV sitting next to otherwise all-OGG packs, re-encoded to OGG Vorbis at the same sample rate (-1.8 MB total, same 12 s loop). About 2 MB off so far, lossless both times (derlendi)
- 1.0.160 Office is now its own extra app too: a launcher icon (off by default, same switch as Browser/Music/... in Settings > Extra apps), reusing the same hub (new document, open, scan, recent) it already had inside Pulse. New: PDF Tools inside Office — Merge PDFs, Split / extract pages, Images to PDF. Built on Android's own PdfRenderer + PdfDocument (the same technique the PDF viewer's "Save a copy" already used), so no PDF library was added and the APK did not grow; the trade-off is that pages come out as pictures, not text-selectable. "Pause on headset disconnect" in the music player was already on by default (Settings > Bluetooth and headsets) — nothing changed there (derlendi)
- 1.0.161 Installer: fixed "Permission denied" / INSTALL_PARSE_FAILED_NOT_APK with root on some devices (KernelSU / Magisk isolated mount namespace, some hardened ROMs) — the root shell's own mount view does not always reach Pulse's private cache either, so the silent install now always stages the APK on shared storage first (root included), and a failed read now aborts with a clear message immediately instead of silently writing an empty split. Install window: halved the gap between the stacked buttons (Install/Upgrade/.../Cancel). Music player: tapping a song (search included) now opens the full-screen player, instead of just starting it quietly in the background. Browser: when dark mode is on, a small forced-dark stylesheet now also blacks out headers/nav bars WebView's own force-dark leaves alone (a header painted through an image or script often survived as a plain white strip before). Keyboard: comma's long press opened voice typing, which already has its own key elsewhere; it now opens the key layout picker (QWERTY / game / numpad) instead, with a matching hint icon. The ⋮ menu's items were already reorderable and addable (Settings > Keyboard, hold and drag a tile, or add from the row below) — nothing to add there (derlendi)
- 1.0.162 Keyboard: the comma key's long press is voice typing again (1.0.161 had wrongly turned it into the layout picker). The top-left arrow's tool bar now starts with Clipboard, then a new Gamepad key (the mic left the bar, the comma key has it): it opens a controller panel over the app with no board behind it — D-pad, A B X Y, Select / Home / Start, L1 L2 R1 R2 and two sticks, every control holding its real key code while the finger is down (D-pad arrows, left stick W A S D, right stick I J K L), wide when the phone lies sideways and narrower upright, with a − / + size control (60-150%, remembered). Existing installs get the new bar order once (voice leaves the bar, clipboard and gamepad lead); the gamepad is also in the ⋮ menu list. Extra apps list: every row now has its visibility switch — added Launcher, Palette and Device troubleshoot (they had none or no row), and the second Office row is named "Office (own app)" to tell it from the page one. Main screens: volume up opens the left panel (Windows 10/11 sidebar), volume down opens the ⋮ menu (the first menu in the classic looks), and the system volume bar no longer shows there (derlendi)
- 1.0.163 Keyboard: the long press of the comma, the period and the key beside ?123 now runs an action picked in Settings > Keyboard (voice typing, clipboard, gamepad, key layout, translate, emoji, language, menu, settings, theme, resize, incognito, or none to keep the key's own pop-up characters); each key wears a small icon of its action above it, like the comma's mic did. The comma stays voice typing by default, the others start on none. Extra apps: Punk Store (the author's own app, it was never inside Pulse) is listed as a linked app — Open when installed, its release page otherwise (derlendi)
- 1.0.164 Launcher is back in Extra apps: its row (opens the launcher's settings) vanished from Settings > Extra apps when its old app-drawer alias was removed in 0.10.134; it is now a tool of the card with its own switch and the ⋮ Apps group lists it, on by default (derlendi)
- 1.0.165 Punk Store is no longer listed in Extra apps (it was never part of Pulse). The switches of the Extra apps list now mean "shown": on for an app or tool placed anywhere (menu, Extra apps group, left menu), off only when hidden — before, a tool placed in the Extra apps group (Launcher among them) showed its switch off although it was listed (derlendi)
- 1.0.166 (batch-1.0.166 branch) Files: the file-extension switch moved into the sort/filter popup, with a hidden-files switch beside it (the header row loses the ext icon). Volume keys: an open menu no longer lets volume down change the volume — the menu is not focusable any more, so the key reaches MainActivity and closes it. Keyboard: Gamepad and Clipboard trade places in the bar (gamepad first). Browser: a Share button opens a bottom sheet with Share link, Copy link and Open in another browser (derlenmedi)
- 1.0.167 (batch-1.0.166 branch) Browser: the share sheet is taken back (it was meant for Files). Files: Share opens a bottom sheet with three options — share the files, copy their paths, share their names as text. Gamepad: a bigger panel (default 130%, taller), its buttons have no background any more (transparent, outlines only). Emoji page: opaque, so the gamepad and the bar behind it no longer show through (derlenmedi)
- 1.0.168 Release bot: the release notes come from YAPILACAKLAR.md now (every entry since the previous release), not from the old RELEASE_NOTES.md that still said 1.0.149; the workflow actions moved to their current versions (checkout, setup-java, setup-gradle and upload-artifact v5), so the Node 20 warnings are gone (derlenmedi)
- 1.0.169 Files: grid names no longer overlap (each tile clips to its own cell), the three dots sit in the tile's top corner instead of a row below. The view button opens a menu: List, Grid, Detailed list (with folder sizes and dates), and "Only for this folder" that remembers the view per folder. Gamepad: a dead zone slider (5-50%) and a sticks height slider in Settings > Keyboard. Extra apps: a long press on a row hides it from the list (a line at the bottom shows the hidden rows again; a long press brings one back) (derlenmedi)
- 1.0.170 Keyboard: a long press on a suggestion now buzzes (a short, firmer pulse) as it starts, so the hold is felt before the word is saved or banned (derlenmedi)
- 1.0.171 Files: date format has a fourth choice, the full day and time always (the Smart one still writes only the time for a file changed today). Settings > Files: sizes and dates in the rows can be switched off, and a live example (a folder and a file) at the top shows what the rows will look like with the choices below. The item count of folders can be switched off in settings, and per folder from the view menu ("Item count in folders here") (derlenmedi)
- 1.0.172 Files: folder rows and file rows have their own size and date switches (Settings > Files: Folders: item count / size / date, Files: size / date); the item count of folders also per folder. Not done yet: a floating, draggable player window; the surroundings of the Winamp, Poweramp, AMOLED and Windows looks are to be transparent (not black) (derlenmedi)
- 1.0.174 Messages: the attach and send buttons of the compose row have a black round background with a white glyph. Not changed yet: the lyrics notification, the keyboard plus key and the long-word keyboard issue (their description was unclear) (derlenmedi)
- 1.0.175 Lyrics: the floating lyrics window has a fixed width of 90% of the screen and up to four lines, so a long line goes on to the next line instead of running off the screen (derlenmedi)
- 1.0.176 Files: the view menu has "Names only here": in that folder the rows show the name alone (no size, date or count). The smart date writes the month in full (not a short form) (derlenmedi)
- 1.0.177 Music player, Winamp look: the playlist window's track names and times use the pixel font (Press Start 2P) instead of the system sans font (derlenmedi)
- 1.0.178 Music player: the side "Lyrics" tab on the Material and Windows looks is removed (lyrics still open from the notification) (derlenmedi)
- 1.0.179 Music player, Material look: the back arrow at the left of an album or artist header is removed (the header still goes back when tapped, and the system back works) (derlenmedi)
- 1.0.180 Music player, Material look: the settings button at the top right of the player is removed (the other looks keep theirs) (derlenmedi)
- 1.0.181 Music player: the Poweramp look has a preview in the look choice (black, dark panel, blue accent); the look names wrap onto a second line instead of stretching one long line (derlenmedi)
- 1.0.182 Keyboard settings: under each long-press choice (comma, period, key beside ?123) a small key shows what the press does, with the action's icon and its name, and both change with the choice (derlenmedi)
- 1.0.183 Files settings: the preview is detailed — a root folder, a folder, a folder shown by name only, and a file changed earlier and today — each with its caption and the size, count and date the choices below give it (derlenmedi)
- 1.0.184 Extra apps store: the list shows only the modules already downloaded (the rest stay in the store), without the short description under each name; on Windows 11 the rows have no card behind them, they sit on the see-through window (derlenmedi)

---

## 🆕 BÜYÜK PAKET (2026-10-29): tüm hatalar + yeni özellikler, tek koordineli sürüm
Durum: 🔴 açık · 🟡 kısmen · 🟢 kodda hazır (hiçbiri cihazda denenmedi). Bir madde bitince buradaki işaret güncellenir.

### 1 Klavye (en yüksek öncelik)
- 🟡 1.1 Noktalama sonrası kelime ekleme/silme hatası: sebep bulunacak (InputConnection/composing/seçim senkronu), eski düzeltme sonucu yeni metne uygulanmayacak, silinen kelime geri gelmeyecek — ilk düzeltme: Space artık yalnız alan gerçekten son kelimeyle bitiyorsa tahmin ekler, noktalama/silme sonrası hatırlanan kelime unutulur (derlenmedi)
- 🔴 1.2 Türkçe düzeltme motoru: kök+ek analizi, harf atlama/tekrar/yer değişimi, komşu tuş, ç ğ ı i ö ş ü, önceki-sonraki kelime bağlamı, emin değilse dokunma
- 🟢 1.3 (cümlenin kelimeleri oy verir, tek kelime dili değiştirmez, ayarda kapatılabilir) Türkçe/İngilizce cümle bağlamı ile ayrım (tek kelime dili değiştirmez), otomatik algı ve elle seçim ayrı
- 🟢 1.4 Öğrenme modları (kelime / cümle / ikisi / kapalı) yapıldı; öğrenilen kelimeleri görme-silme-ekleme, listeyi kopyalama/yapıştırma yapıldı; "doğru işaretle" = elle ekleme; hariç tutma ve geri alma mevcut (düzeltmeyi geri al / bir daha önerme)
- 🟡 1.5 (geri alınan düzeltme bir daha yapılmaz) Gecikmeli düzeltme: yüksek güvenli düzeltme anında, öneri çubuğu ile metin senkron
- 🟡 1.6 Öneri çubuğu: 2 tam öneri, üç nokta ile kesme yok, tekrar/alakasız yok, e-posta önerisi sürekli çıkmaz — uzun kelimeye daha çok yer, sığmazsa üçüncü öneri çekilir; kayıtlı e-posta yalnız e-posta alanında (derlenmedi)
- 🟢 1.7 E-posta anahtarı: bar/menü/basılı-tutma tuşu veya ek tuş satırına atanabilen "@" + alan adı tuşu, imleç noktasına ekler, önceki yazıyı silmez; alan adı Ayarlar > Klavye > Yazım; kayıtlı e-postalar yalnız e-posta alanında çıkar
- 🟡 1.8 Pano: ekran görüntüsü gerçek PNG/JPEG, raptiye ikonu, kısayol ikonu, Ctrl+X panoya, donanım klavyesi ve basılı tutma aynı geçmiş, kısayol alanında imleç — PNG/JPEG, raptiye, kısayol ikonu, kes/kopyala geçmişe, kısayol alanı imleci yapıldı; donanım klavyesi geçmiş birleşmesi doğrulanmadı

### 2 Temalar / AMOLED
- 🔴 2.1 Ayrı Material / Windows 11 / AMOLED siyah teması + AMOLED vurgu rengi; tüm ekranlar (çubuklar, hızlı panel, ayarlar, menüler, editörler, PDF/Office, müzik, ağ, geçmiş, pencereler, klavye); başka temanın rengi sızmasın
- 🟡 2.2 Kontrast: End Task (kırmızı zemin beyaz yazı) ve indirme silme penceresi düğmeleri yapıldı; genel otomatik metin/düğme rengi taraması sürüyor
- 🔴 2.3 Tam siyah, vurgu, kart arka planı, saydam katman, kontrast, animasyon yoğunluğu seçenekleri

### 3 Dosyalar / APK / ZIP / Office
- 🟢 3.0 Recording/çağrı kayıtlarında 3 nokta menüsü ile paylaşma (diğer klasörler gibi)
- 🟢 3.1 APK ayrıntı: Install tek satır (yapıldı: IButton tek satır), Browse/Install düzeni; APK/ZIP/diğer için tutarlı menü; seçim ekranlarında çoklu seçim; sil/kopyala/taşı/paylaş/arşivle
- 🟢 3.2 Medya araçları > PDF sekmesi: birden çok PDF birleştirme ve sayfa aralığı kesme (sayfalar resim olarak kopyalanır, yazı seçilemez); resimlerden tek PDF zaten Dönüştür sekmesinde. PDF sayfa/görsel düzenleme ve Tools bölümüne taşıma açık
- 🟡 3.3 PowerPoint açma/görüntüleme/slayt gezme/düzenleme, Office araç çubukları, tam siyah tema

### 4 Paylaşma / Kaydetme / Yedek
- 🟢 4.0 Share / Save As kontrolü; Save As AMOLED'de gri-beyaz kalmasın; hata bildirimi
- 🟡 4.1 Yedek: kapaklar, arama dizinleri, önbellekler ve sözlükler zaten yedeğe girmiyor; boyut gösteriliyor; kayıtlı yedek listesi (boyut, tarih), tek tek silme ve "en yeni ikisi hariç sil" eklendi. Müzik dosyaları hiç kopyalanmıyor

### 5 Video oynatıcı
- 🟢 5.0 (Cast = telefonun ekran yansıtma ekranı; videoyu devretmez) Alt kontroller için yarı saydam katman, beyaz ikon kontrastı, 3 nokta menüsü düzeni, Cast düğmesi (gerçek), dokunma alanları

### 6 Saat uygulaması
- 🟡 6.0 Saat (Extra apps) = Lucid Alarm kodu ve görünümüyle Pulse içine kopyalandı (extras/lucid, GPL-3.0). Depolama Room yerine JSON dosyası. Derlenmedi: Lucid compileSdk 35 / yeni Compose ile yazılmış, uyum hataları çıkabilir
- 🟢 6.1 Alarm AlarmManager.setAlarmClock ile kuruldu, açılışta/güncellemede yeniden kurulur. Sınır: "Zorla durdur" yapılırsa hiçbir uygulamanın alarmı Pulse açılana kadar çalmaz (Android kuralı); cihazda denenmedi

### 7 Network Manager
- 🟢 7.1 Ağ kullanımı: gün (sayfa sayfa) / hafta / ay seçimi, pasta grafik, uygulama listesi, Wi-Fi ve mobil ayrı (NetworkStatsManager gerçek verisi)
- 🟡 7.2 Uygulama başına mobil/Wi-Fi engelleme: root ile iptables (uygulamaya dokununca iki anahtar), kural geri okunur; rootsuz mümkün değil diye yazılır; yeniden başlatmada sayfa açılınca geri yüklenir (boot alıcısı yok); cihazda denenmedi
- 🟡 7.3 (hata mesajı artık nedeni ayırıyor: yetki / komuta yanıt yok / modem kabul etmedi; desteklenmeyen türü gizleme sürüyor) Ağ türü değiştirme: "Phone didn't take it" nedeni, desteklenmeyeni gizle, izin/modem hatası ayrımı, sahte başarı yok

### 8 Extra Apps menüsü
- 🟢 8.0 Görünürlük anahtarları (uzun basma ile gizleme ve ızgara/liste seçimi yapıldı); ızgara düzeni + liste seçeneği, App Info/Open düğmesi kaldırma, Office kaldırma, kaydırma konumu koruma
- 🟡 8.1 Mağaza: yalnız indirilenler (yapıldı); kurulu → aç, kurulu değil → Download, güncelleme bilgisi/sürüm karşılaştırma, APKMirror doğrudan sayfa

### 9 Toolkit
- 🟢 9.1 Hız testi: geçmiş sayfası (günlük gezinme, ortalama, silme), kayıtlı sonuçlar, hata nedeni yapıldı; Toolkit içine taşıma ve Quick Panel kısayolu (karo var) açık
- 🟢 9.2 Yüzen sayaç: geri sayım, başlat/duraklat/sıfırla/kapat zaten vardı; küçültme (%50-140), saydamlık, arka plan rengi, yalnız süre/düğmeli seçeneği ve konumun hatırlanması eklendi

### 10 Müzik
- 🔴 10.1 Yedekte müzik/kapak çoğaltma yok
- 🔴 10.2 Widget: yeniden boyutlandırmada düzen, kapağa/ada dokununca doğru oynatıcı, düğmeler gerçek durumu yönetir, 5 sn ileri/geri korunur, göster/gizle, ayar
- 🟡 10.3 (yıldızlar %15 büyütüldü, hap büyüdü; ilerleme çubuğu güncelleme sıklığı 400 ms'den 100 ms'ye indi) Yıldız dokunma alanı + %15 büyüt, ilerleme çubuğu akıcılığı
- 🟡 10.4 Ruh hâli etiketleri: üzgün, sakin/dingin, nostaljik, romantik, öfkeli, hayalci eklendi (tür etiketi ve başlık sözcüklerinden, 1960-99 yılları nostaljik); kitaplık arayüzü, etiket düzenleme ve çevrimiçi etiket açık
- 🔴 10.5 Çevrimiçi arama/çalma listesi: iki oynatıcı sorunu, gerçek sonuçlar
- 🟢 10.6 Müzik özeti (Extra apps): 6 sayfa (giriş, dakika, sanatçılar, şarkılar, saat/gün dağılımı, kart), 30 gün / 3 ay / 1 yıl, kartı PNG kaydetme. Dinleme kaydı bugünden başlıyor (geçmişi uydurulmaz). Ruh hâli/tür dağılımı, otomatik üç aylık özet ve paylaşma açık

### 11 White Noise
- 🟡 11.0 (Meditation %5 azaltıldı) Waterfall, Gentle Rain, Wind Chimes iyileştirme; Meditation %5 azalt; Seaside aynı; yeni doğa sesleri; profiller ikonlu; kişisel karışım
- 🟢 11.1 Bildirimde durdur, dokununca doğrudan Music Player, bildirim kaybolma nedeni

### 12 İndirme yöneticisi
- 🟢 12.0 Silinen dosya listede kalmasın (2,5 sn'de bir kontrol), Delete/Cancel kontrastı, uzun basıp çoklu seçim + toplu silme, onay, silinemeyen dosya uyarısı

### 13 Uygulama geçmişi
- 🟢 13.0 (rapor CSV dışa aktarma, kategori = uygulamanın kendi bildirdiği; günlük süre sınırı: App history'de uygulamaya dokun, süre dolunca ana ekrana atar, gece yarısı sıfırlanır; ikonlar, pasta+liste birlikte, bugün/hafta/ay, App Info/Force Stop/Extract/Uninstall ikonlu düğmeler ve uygulamanın kendi ikonu ayrıntıda yapıldı; kategori raporu, dışa aktarma, süre sınırı, App Info/Force Stop ikonları sürüyor) Total/Screen On ikonları, pasta + liste, bugün/hafta/ay, kategori raporu, dışa aktarma, süre sınırı, liste önbelleği, App Info/Force Stop/Extract/Uninstall ikonlu, uygulama ikonu ayrıntıda

### 14 Gamepad
- 🟢 14.0 Joystick konumu/boşluk (kısmen yapıldı); floating gamepad, boyut-konum ayarı

### 15 Device paneli
- 🟡 15.0 (Folder Sizes tekrarı Storage sayfasından kaldırıldı; cihaz kartı sürüyor) Overview üstüne cihaz kartı; Device menüsünden Folder Sizes tekrarını kaldır
- 🟡 15.1 Sıcaklık: pil ve işlemci ayrı çizgilerle, bugün/dün/evvelsi gün geçmişi, en sıcak an (dakikada bir kayıt, yalnız Pulse çalışırken); termal durum zaten ayrı gösteriliyor

### 16 Root / izin / arka plan
- 🟡 16.0 (Ayarlar > "İzinleri koru" anahtarı: root ile appop yazar, rootsuz sistem ekranını açar) Otomatik izin sıfırlama, erişilebilirlik servisi yeniden başlatma, boot sonrası servisler, kapatma/atma/zorla durdurma ayrımı

### 17 Motion Cues / Hızlı panel
- 🔴 17.0 Motion Cues panel kapanana kadar sürsün, doğru geri dönüş ekranı, Quick Panel ikon/tema uyumu

### 18 Launcher
- 🔴 18.1 Windows Phone teması: sol panel sadeleştirme, Windows tuşu, tam ekran, widget yerleşimi, ayar dönüşünde liste başa atlamasın
- 🟡 18.2 (End Task yapıldı) Windows 11: End Task, vurgu kontrastı, tutarlı menü
- 🟡 18.3 (menü ikonları ve tuş takımı konumu yapıldı; otomatik yardımcı açık) Grafik tema: otomatik yardımcı kapatılabilir, Scientific/Programmer ikonları, tuş takımı %15 yukarı
- 🟡 18.4 (başlangıç sesi her görünümde bir kez çalar; ayar mevcut) Launcher sesleri ayarı, tek sefer tetikleme
- 🟡 18.5 (Music başlığı belirginleşti) Not widget'ı genel widget, boyut, Music başlığı belirgin, kategoriler, WMP widget düğmesi, önceki/sonraki
- 🟡 18.6 Tüm uygulamada liste dönüşünde kaydırma/arama/filtre korunsun

### 19 Paint / ikon üretici
- 🟡 19.1 Paint: sprey, sulu boya, yinele eklendi (kalem, fırça, silgi, boya kovası, geri al, PNG kaydı zaten vardı); tuval boyutu/arka plan seçenekleri ve tam renk seçici açık
- 🔴 19.2 Material ikon üretici: arama, kategori, kontur/boyut/renk, PNG, kare çıktı, lisans

### 20 Tarayıcı / WebView
- 🔴 20.0 WebView sağlayıcı incelemesi, sistem geneli değiştirme sınırları, reklam engelleme + karanlık mod, uzantı desteği ayrımı

### 21 Bildirim geçmişi / ikonlar
- 🟡 21.0 (saat ikonu menüde yapıldı) Win11 tarzı saat ikonu, geçmiş çalışır, gereksiz 3 nokta menüleri, ayar başlıklarında ikon, büyük yazıları küçült

### 22 Punk Store
- 🔴 22.0 18 MB → 75 MB kurulum boyutu nedeni, kurulum yöntemi kalıcı, varsayılan root, root yoksa doğru hata

### 23-24 Genel kalite ve teslim
- 🔴 23.0 Gereksiz yeniden çizim, ikon önbelleği, tema/vurgu ortaklaştırma, ANR/çökme, boyut
- 🟡 24.0 (klavye benzerlik/yer değiştirme birim testi eklendi) Birim testleri (klavye noktalama/dil), tek sürüm derleme, sonuç raporu (APK konumu/boyut, bağımlılık-lisans, mimari)
- 1.0.185 Keyboard: Space no longer writes a predicted word unless the field really ends in the last word typed; after a sign or a delete the remembered words are dropped, so no deleted or extra word comes back after a full stop or comma (derlenmedi)
- 1.0.186 Keyboard bar: long words get more room and the third suggestion steps aside when three would not fit, so words are not cut with dots; saved e-mail addresses are no longer offered in ordinary text fields (derlenmedi)
- 1.0.187 Keyboard: a new "Direction keys" layout (Settings > Keyboard): nine big keys, a tap types the centre letter and a drag towards a corner or side types the character shown there (English and Turkish letters, shift/caps/copy/cut/paste/undo/redo by swipe, emoji key with settings/menu/language/voice swipes, backspace with repeat, space with cursor drag) (derlenmedi)

### 📦 Yüklenen dış uygulamalar (2026-10-29, birinci parti — ikinci parti gelecek)
Her biri için amaç çıkarılacak; kaynak zip'lerin lisansı Pulse'a kod almadan önce denetlenecek (GPL-3.0 olanlar Pulse'ın lisansıyla uyuşmayabilir). Henüz hiçbirinden kod alınmadı.
- 🔴 Baridmail-App (kaynak, Java): tek kullanımlık e-posta; gelen kutusu, çoklu hesap, bildirim servisi
- 🔴 grabbit (kaynak, Kotlin): yt-dlp ön yüzü, biçim seçimi — Pulse'ta kendi YouTube indiricisi yazılmayacağı kuralı geçerli, yalnızca Extra Apps modülü adayı
- 🔴 MetroWinLauncher (kaynak, GPL-3.0+): Windows Phone Start ekranı, canlı döşemeler, Action Center — launcher Windows Phone teması için referans
- 🔴 relay (kaynak): telefon-bilgisayar dosya/pano aktarımı (yerel Wi-Fi)
- 🔴 Savr (kaynak, GPLv3): yer imi uygulaması
- 🔴 APK: Battery Supervisor, BigText, ChatNotes, Device Info, Groundd, Multi Toolbox, My Vinyl, Outplay, Punto, RadioSquare — her birinin özelliği incelenip Pulse'taki karşılığı/eksiği çıkarılacak
- 1.0.188 Fixes: Install button stays on one line; End task is white on red; Downloads list drops files that were deleted, long press picks several rows with one delete question, Delete/Cancel buttons readable in every look; call recordings rows get a three-dot menu (share, copy path); video player: soft dark shade behind top and bottom controls, a Cast button (the phone screen-cast picker), the More menu no longer repeats speed, repeat and sleep timer; network type failure says why (permission, no answer, modem refused) (derlenmedi)
- 1.0.189 Fixes: Good Lock list has Installed / Not installed / Updates tabs (tap opens the module, Download for the missing ones, the update check says why when it fails, release page from the feed); the launcher plays its start-up sound on every look once and never twice in a row; calculator Scientific and Programmer menu icons were missing (now Functions and Terminal); calculator keys sit higher (derlenmedi)
- 1.0.190 Keyboard clipboard: screenshots from the keyboard are saved as real PNG/JPEG files (not ".img"); a cut or copy from the keyboard is added to the history at once; pinning uses a push-pin icon and shortcuts a bolt icon; the shortcut name field shows a caret (derlenmedi)
- 1.0.191 App history: icons for total and screen-on, today / week / month, the ring and the list in one scroll; Storage page no longer repeats Folder sizes (it lives in Files); the Meditation mix is 5 percent quieter (derlenmedi)
- 1.0.192 Ambient sound notification: it has a Stop button, and a tap opens the music player's sound page instead of the Pulse start page; the music keep-alive notification opens the player (derlenmedi)
- 1.0.193 Settings: a "Keep permissions" switch (Android's permission auto-reset exemption: written with root, otherwise the system screen opens); Notification history uses a clock icon in the app list (derlenmedi)
- 1.0.194 Keyboard learning: choose what is learned (words, sentences, both, off); a Learned words list with search, delete, add by hand, copy out and paste in (derlenmedi)
- 1.0.195 Keyboard: the language of a sentence is worked out from its words (two words of one language and twice the other's votes), so Turkish sentences are corrected as Turkish and one English word changes nothing; a switch turns the detection off (derlenmedi)
- 1.0.196 Speed test: every finished test is saved; a history page (the clock icon) goes day by day with the averages and a delete for each test; a failed test says why (no internet, timeout, secure connection) (derlenmedi)
- 1.0.197 Network usage: Day / Week / Month, a pie of the biggest apps for the week and the month, and a tap on an app opens two root switches (block mobile data / block Wi-Fi) that are read back from the firewall; without root it says Android has no other way (derlenmedi)
- 1.0.198 Paint: a Spray tool (a cloud of dots, sized by the width) and a Watercolour tool (wide see-through strokes that deepen where they cross), Redo next to Undo, 20 undo steps (derlenmedi)
- 1.0.199 Floating timer: smaller by default, a size slider, see-through slider, four background colours, a switch to show only the time or the buttons too, and it comes back where it was left (derlenmedi)
- 1.0.200 Keyboard: when the cursor comes back to the end of a word (a tap behind it, or a delete that stops there) the word opens again and stands in the middle of the suggestion bar, and typing goes on with it instead of starting a new word. New tool Charging power (Extra apps): live watts into or out of the battery, voltage, current, temperature, peak in and out, a 60 second graph and the time to full, drawn with a cable of flowing dots (reading method after WattFlow, MIT) (derlenmedi)

### 📦 Yüklenen dış uygulamalar — ikinci parti
- 🟢 wattflow (MIT): Şarj gücü aracı olarak Pulse içine yazıldı (okuma yöntemi ondan, ekran Pulse'un), lisans metni assets/licenses içinde
- 🟡 Lucid-Alarm (GPL-3.0, Pulse ile uyumlu): Saat modülü için kaynak; alarm, kronometre, zamanlayıcı, dünya saati, Nowbar. Pulse'a alarm + dünya saati olarak aktarılıyor
- 🔴 SoundPlus.apk (kapalı kaynak, StartApp reklam SDK'sı içeriyor): kod alınamaz; yalnız özellik referansı
- 🔴 Bionic Tape ekran görüntüleri (kaset çalar görünümü): müzik oynatıcıya kaset görünümü fikri olarak not edildi
- 1.0.201 New Clock (Extra apps): real system alarms (repeat days, snooze, vibration, an alarm screen over the lock screen, set again after a reboot), world clocks, and the stopwatch and timer in one place; Android asks for the exact-alarm and full-screen permissions and the screen says so when one is missing (derlenmedi)
- 1.0.202 Clock is now Lucid Alarm itself (GPL-3.0, DarkAspect1): its code and look are copied into Pulse (alarms with groups, custom ring screens with wallpapers, fonts, colours and animated backgrounds, wake check, upcoming alerts, recycle bin, stopwatch with laps, timer with presets, world clock, Nowbar, backup and restore, its themes and accent colours); only the storage differs (a JSON file instead of Room). The simple clock written before is removed (derlenmedi)
- 1.0.203 Music player: a new Cassette look (a yellow tape deck: a cassette whose two reels turn while the music plays and whose tape moves from the left reel to the right with the song, a hand-written label with the song and artist, raised deck keys for previous / play-pause / next / eject, shuffle, repeat, volume, and a tape shelf that lists the library); drawn by Pulse after the cassette players of the 80s and the Bionic Tape screenshots, no code of another app (derlenmedi)
- 1.0.204 Music player: Extra themes, listed together in the look picker: Cassette in six colours (yellow, red, blue, mint, pink, black), iPod with a working click wheel in five colours (menu list, previous, next, play/pause, turning the ring moves in the list or changes the volume), and Turntable in three (a record that turns, a tonearm that moves in with the song); more can be added to the same list (derlenmedi)
- 1.0.205 App size: 75 wallpaper and splash pictures (drawable-nodpi) turned from JPEG to WebP (7.0 MB to 3.7 MB) and the 10 Windows Media Player skin pictures to lossless WebP (3.1 MB to 2.0 MB), 4.4 MB less in the APK with the same look (derlenmedi)
- 1.0.206 New tools: Radio (Extra apps: search the open station directory, pick a country, play through the music player so it goes on in the background with the media notification, favourites; a failed search says why) and Big text (Toolkit: a message across the whole screen in huge letters, still or running like a signboard, six colour pairs, screen stays on) (derlenmedi)
- 1.0.207 Keyboard: a sign or a number typed right after a word no longer replaces the word: the word is closed where it stands and the sign is added after it as one batch (before, the commit relied on the app replacing the composing text, which some fields do wrongly: the word vanished or a second word appeared); with 1.0.185 the predicted word is no longer written after a sign or a number (derlenmedi)
- 1.0.208 Keyboard: Backspace right after an automatic correction takes it back (the word as typed opens again) and that correction is remembered as unwanted, even for swapped letters, so the same word is not corrected again; no space is added by itself after a sign any more (off by default, also for those who had it on; the typing setting can turn it on again) (derlenmedi)
- 1.0.209 Music: the Poweramp rating stars are 15 percent larger with a taller touch pill (44 dp, five cells of 34 dp), and the progress bars of the Material, Poweramp and music page players refresh 3 to 4 times as often, so they move smoothly (derlenmedi)
- 1.0.210 Backup: Settings > Data lists the saved backups with their size and date, deletes one, or deletes all but the newest two (derlenmedi)
- 1.0.211 Temperature tool: a history card with the battery and processor lines for today, yesterday and two days ago, the range of each and the hottest moment; readings are noted once a minute while Pulse runs and kept three days (derlenmedi)
- 1.0.212 Media tools: a PDF tab that joins several PDFs into one and cuts a range of pages out of one (the pages are drawn as pictures, so the text cannot be selected; said on the screen); saved in Download/Pulse (derlenmedi)
- 1.0.213 Music: six more moods next to energetic, chill, focus and happy: sad, calm, nostalgic, romantic, angry and dreamy (found from the genre tag and the title in English and Turkish; songs from the 60s to the 90s count as nostalgic); each gets its own mix and can be chosen in the mix settings (derlenmedi)
- 1.0.214 Music recap (Extra apps): from now on every real listening is noted with its time; the recap shows pages to swipe through (minutes, top artists, top songs, the hours and days you listen, a card) for the last 30 days, 3 months or a year, and saves the card as a PNG in Download/Pulse; it says from which day the notes count (derlenmedi)
- 1.0.215 Extra apps list: a switch in its top right shows the apps as tiles (icon and name, three in a row) or as rows with their description; the choice is kept, and a long press still hides or shows an app (derlenmedi)
- 1.0.216 Keyboard: an e-mail key that types "@" and a domain you choose (gmail.com by default) at the cursor, without touching what was typed before; it can sit on the bar, in the menu, in the extra key row, or on a long press of the comma, period or the key beside ?123 (derlenmedi)
- 1.0.217 Widgets page: the Music category title is larger, in the accent colour, with its icon and a line under it (derlenmedi)
- 1.0.218 App details: the app's own icon beside its name, and Open, App info, Force stop, Extract APK and Uninstall are icon buttons instead of words (derlenmedi)
- 1.0.219 App history: a tap on an app sets a daily time limit (off, 15 min to 3 h); when the time is up the App lock helper sends the phone back to the home screen with a message, until midnight (needs usage access and the helper; background music and downloads go on) (derlenmedi)
- 1.0.220 App history: the download icon saves the shown range as a CSV (app, package, category as the app declares it, minutes, total) in Download/Pulse (derlenmedi)
- 1.0.221 Save as dialog: explicit text/button colours and window panel colour so AMOLED no longer shows grey/white (derlenmedi)
- 1.0.222 Keyboard unit test (isSwap/similar); Gamepad size slider in keyboard settings; Music recap mood+genre page (7th); Extra apps list keeps its scroll; Paint paper colour (File menu, Windows looks) (derlenmedi)
- 1.0.223 Build fixes: itemsIndexed import, TapeShelf accent, FlowRow opt-in (derlenmedi)
- 1.0.224 Files: Recent items get previews (pictures, video frame, PDF first page, song cover, APK icon, Office/ODF thumbnail) and a small type badge (black, white line icon) on every file (derlenmedi)
- 1.0.225 File type badges in Files: black ground, white outline icon, thin white edge (were coloured); Windows 11 Fluent icons for Clock, Radio, Music recap, Charging power, Browser and Office in the ⋮ menu and the Extra apps list (derlenmedi)
- 1.0.226 Keyboard: after Space/punctuation the word is checked and written back when the field swallowed it (derlenmedi)
- 1.0.227 Files: Detailed list always shows type, size and date (also in folders set to names only); Settings > Files > View is a picture picker (list, grid, detailed) (derlenmedi)
- 1.0.228 Lyrics: separate lyrics notification on by default, a song with no lyrics found is searched again after a minute (was never); widgets/notifications open the music app even when its old recents card has no window (derlenmedi)
- 1.0.229 Dialog buttons in the Windows looks: End process / Cancel text takes the colour that reads on the button (white on the accent fill), not danger red (derlenmedi)
- 1.0.230 Office tools: Scan document and PDF tools in one page with an icon; settings (page size A4/Letter/as picture, margin, sharpness, page numbers, scan start filter, trim, open after saving, name start); scan filters Colour/Original/Grey/Clean/B&W/Light, paper cut out and flattened with an edge trim, turn and remove page (derlenmedi)
- 1.0.231 Dialog accent button (Win11): text is always white (derlenmedi)
- 1.0.232 Multi APK install is the same floating window as a single APK (InstallMultiWindow, from Files and InstallActivity): rows with state and tick, Install N apps; accent dialog buttons use black on a light accent and white on a dark one; App manager: quick chips Recent/All/User/System, long-press select, select all, Extract APKs (derlenmedi)
- 1.0.233 Office: PowerPoint and PDF get the Word-style ribbon (groups with captions: PDF View Zoom/Pages/Display, Edit Mark up/Text/Colour/Edit with new Fit width, First/Prev/Next/Last, Dark pages, Clear marks; PPT Slides/Font/Paragraph/Clipboard/...); Settings > Office > Title bar and ribbon: accent colour (new default), app colour, AMOLED black for the bar and the ribbon strips (derlenmedi)
- 1.0.234 Office title bar option works in every Office look and is optional: As the look draws it (default), Accent, App colour, AMOLED black (bar, tabs, ribbon, status bar) (derlenmedi)
- 1.0.235 Office: AMOLED black option also covers Excel (formula bar, sheet tabs); an arrow in the title bar hides the whole top block by hand, a small handle at the top brings it back (derlenmedi)
- 1.0.236 Files Share sheet: the apps that take the share in a row at the top, share as one zip (folders too), Bluetooth, copy names, copy paths, names as text; the file menu Share opens it too (derlenmedi)
- 1.0.237 Files Share sheet: Nearby devices (Pulse LocalSend) row, and the nearby senders (Pulse LocalSend, LocalSend app, Quick Share) first in the app row (derlenmedi)
- 1.0.238 Relay (Extra apps, tool "relay"): the Relay engine (Apache-2.0, 4800 lines, com.vythera.relay.*) copied into the app with kotlinx-serialization + BouncyCastle (bcpkix) added; Android side: RelayHost (identity, pairings, inbox Download/Pulse/Relay), RelayService (stay available, notifications), RelayScreen (devices, pairing code, send files/text, accept/decline/pause transfers, received text, connect by address, clipboard sync). Needs Android 10+. Derlenmedi: kotlin serialization plugin and BouncyCastle under R8 are the first things to check
- Yüklenen Archive.zip / Archive_1.zip (iki kopya): CodeAssist, Moon+ Reader Pro (APK açılımı), VeriSky (APK açılımı), CalibRaw, Vyxel Apps (GitHub uygulama mağazası, AGPL-3.0) — henüz incelenmedi
- 1.0.239 Quick Settings: the Coin tile shows heads or tails (new ic_coin_heads/ic_coin_tails) and the Dice and Coin tiles flicker through a few pictures after a tap; "Pulse · Relay" in the system Share list for files, text and links to paired devices, and a Relay row in the Files share sheet (derlenmedi)
- 1.0.240 Keyboard three-dot menu: the head row (back, settings, themes) is removed; Keys stays at the end of the grid (derlenmedi)
- 1.0.241 Sticky notes: the widget redraws when its note changes; the editor can add a new note and delete the open one (its widget goes back to an empty note) (derlenmedi)

### Bu tur eklenenler (1.0.185 – 1.0.241, derlenmedi)
Hepsi kodda hazır; hiçbiri cihazda denenmedi. Bir derleme (`derle`) sonrası hata çıkarsa ilk bunlara bakılır.
- 🟢 Files: Son (Recent) öğelerine önizleme (resim, video karesi, PDF ilk sayfa, şarkı kapağı, APK ikonu, Office içindeki küçük resim) ve dosya türü rozeti (siyah, beyaz çizgi)
- 🟢 Files: detaylı liste her zaman tür, boyut ve tarih gösterir; Ayarlar > Dosyalar > Görünüm resimli üç kart (liste, ızgara, detaylı)
- 🟢 Files Paylaş sayfası: paylaşımı alan uygulamalar satırı (LocalSend, Quick Share öncelikli), tek zip (klasörler dahil), Bluetooth, yolları/adları kopyala, Relay satırı
- 🟢 Toplu APK kurulumu normal Installer penceresi gibi yüzen pencerede (InstallMultiWindow), satır satır durum ve sıralı kurulum
- 🟢 App manager: hızlı çipler (Son eklenen, Hepsi, Kullanıcı, Sistem), uzun basıp seçme, hepsini seç, toplu APK çıkarma
- 🟢 Office: PowerPoint ve PDF Word tarzı şerit (gruplu ikonlu sekmeler); PDF Görünüm/Düzenle sekmeleri
- 🟢 Office: Ayarlar > Üst çubuk ve şerit (görünümün kendi rengi, vurgu, uygulama rengi, AMOLED siyah) her Office görünümünde; Excel'de AMOLED siyah; elle üst çubuğu gizleme oku
- 🟢 Office araçları sayfası: Belge tara (filtreler: Renkli, Orijinal, Gri, Temiz, Siyah beyaz, Aydınlık; kağıdı kesip düzleştirme, kenar kırpma, sayfa çevirme/silme) ve PDF araçları; ortak PDF ayarları (boyut, kenar, netlik, sayfa numarası)
- 🟢 Relay (Extra apps, "relay"): Apache-2.0 Relay motoru + Android tarafı (cihaz eşleştirme, dosya/metin/bağlantı gönderme ve alma, arka planda açık kalma, pano). Sistem Paylaş listesinde "Pulse · Relay". Derlemede ilk bakılacak: kotlinx.serialization eklentisi ve BouncyCastle R8
- 🟢 Hızlı panel: Yazı tura karosu sonuca göre tura/yazı ikonu; zar ve yazı tura dokununca birkaç kare animasyon
- 🟢 Klavye üç nokta menüsünün üst satırı (geri, ayarlar, tema) kaldırıldı
- 🟢 Müzik özeti: 7. sayfa ruh hâli ve tür dağılımı
- 🟢 Müzik bildirimi: ayrı söz bildirimi varsayılan açık; sözü bulunamayan şarkı bir dakika sonra yeniden aranır
- 🟢 Klavye birim testi (isSwap / similar), Gamepad boyut kaydırıcısı
- 🟢 Paint: Dosya menüsünde kâğıt rengi (Windows görünümleri)
- 🟢 Diyalog düğmeleri: vurgu dolgusunda yazı rengi zemine göre (açık vurgu siyah, koyu vurgu beyaz)
- 🟢 Save As diyaloğu: açık renkli metin ve düğmeler (AMOLED'de gri-beyaz sorunu)
- 🟢 Extra apps listesi kendi konumunu korur (bir uygulamaya girip dönünce)
- 🟡 Yapışkan notlar: widget canlı güncelleniyor, yeni ve sil eklendi; görünüm uyumu (her görünümde doğru çizim) ve not listesi açık
- 🟡 Klavye otomatik düzeltme boşluk sonrası kelime yazma: güvenlik önlemi eklendi (kelime geri yazılır); asıl sebep cihazda uygulama bazında doğrulanmalı
- 🔴 Çevrimiçi müzik (10.5), beyaz gürültü yeni sesler (11.0), Motion Cues (17.0), yüzen gamepad (14.0 kısmi), tema seçenekleri (2.1, 2.3), launcher (18.1), tarayıcı (20.0), Punk Store (22.0), ikon üretici (19.2), genel kalite (23.0)
- 🔴 Vyxel Apps, CodeAssist, Moon+ Reader, VeriSky, CalibRaw ve diğer yüklenen arşivler: henüz incelenmedi
- 1.0.242 YAPILACAKLAR: BÜYÜK PAKET statuses updated for the work since 1.0.184, and a "Bu tur eklenenler" list added (derlenmedi)
- 1.0.243 Keyboard: long press on the comma starts speech typing inside the keyboard (SpeechRecognizer, Pulse microphone permission), the keyboard stays up; the comma icon turns accent while listening; falls back to the voice keyboard when there is no permission or recognizer (derlenmedi)
- 1.0.244 Keyboard key sizes: ?123 key 15% narrower and comma key 10% narrower by default (sliders in Settings > Keyboard > Size, 0-40%); the space bar takes the room (derlenmedi)
- 1.0.245 Extra apps: White noise and Notification history rows removed (White noise opens from the Music player's top bar, a volume button); the per-row switch is an eye that hides a row here and in the ⋮ menu; an app whose own launcher icon is on is not listed (it opens from its icon); "Show Extra apps in the ⋮ menu" switch, on by default; Launcher settings get "Open the launcher to test" (derlenmedi)
- 1.0.246 Settings > Extra apps: tool rows show one Fluent glyph in the text colour (no coloured pictures); the switch on lists the tool in Extra apps, off hides it there and in the Apps group; Extra apps lists exactly the tools switched on (derlenmedi)
- 1.0.247 Fluent colour icons for LocalSend (Send), Palette (Paint Brush), Keyboard, BlackOut and Island in Extra apps, the Apps settings and the Windows 11 rows; Send and Paint Brush are the official colour glyphs of the Fluent set, the other three are the filled glyph with a Fluent blue-violet gradient, rendered in the app (derlenmedi)
- 1.0.248 Fluent colour pictures (gradient, rendered in the app) for the rest of the flat Fluent icons: the tools, the Extra apps and the pages (fc_ pictures for relay, clock, radio, recap, chargewatts, browser, mediatools, speedtest, buds, calculator, pages); the Apps settings tool rows use them (derlenmedi)
- 1.0.249 Radio only from the Music player (a radio button on its top bar); removed from Extra apps, the ⋮ menu and Settings > Extra apps (derlenmedi)
- 1.0.250 Charging power moved into Device > Battery (the card at the top of the battery page); the Extra apps row, the ⋮ entry and the Settings row are removed (derlenmedi)
- 1.0.251 Radio inside the Music player takes the player's dark look (white text, lime accent); Music recap moved from Extra apps and the ⋮ menu to a button on the Music player top bar, reading the player's own play log (derlenmedi)
- 1.0.252 Files share sheet: Relay and LocalSend are one entry ("Relay / LocalSend") that asks which one to open; they no longer appear twice in the app row (derlenmedi)
- 1.0.253 Settings > Pages: a tap on a page name opens that page (the switch still decides the left menu) (derlenmedi)
- 1.0.254 Relay and LocalSend only from Files (Files > More rows); removed from Extra apps and the ⋮ menu. Radio: two boombox skins drawn in the app (nostalgic wood and chrome, modern matte with a lit display), chosen with chips above the list (derlenmedi)
- 1.0.255 Windows Phone start (WinPhone look): medium tiles with notifications turn over to the count of new items (from MetroWinLauncher's live tile idea, rewritten for Pulse). Phase 1 of the MetroWin port; action centre and metro lock screen not started (derlenmedi)
- 1.0.256 Device > Overview: tapping CPU, Memory, Temperature, Battery, Storage or Network opens that part; Device > Network has Network usage and Internet speed test (the graph opens Network usage). Both are removed from Extra apps, the ⋮ menu, the Settings tool list, the launcher control panel and the Processes header double tap (derlenmedi)
- 1.0.257 Windows Phone action centre: the notifications are listed (newest first, app name, title, text; a tap opens the app, Clear all), from the notification history; the Metro lock screen (phase 3) not done: Android does not let a launcher replace the lock screen (derlenmedi)
- 1.0.258 Device > Overview: a Security card at the top ("Your phone is secure"; App lock, Authenticator, App history). These three are reached only from there: removed from the side menu, Settings lists, ⋮ menu and Extra apps (derlenmedi)
- 1.0.259 Quick settings Coin and Dice: a tap lights the tile (ACTIVE, white) while the picture turns through its faces, then it settles on the result. Device > Overview: the "Your phone is secure" card is gone; a Security button sits under the Network card and opens Device > Security (App lock, Authenticator, App history) (derlenmedi)
- 1.0.260 Build fix: two Fluent colour pictures had capital letters in their file names (fc_extraCalculator, fc_extraBrowser), which Android does not allow. Renamed to lowercase and the lookups lowercase the key (derlenmedi)
- 1.0.261 Build fix: the Explorer share sheet called sendIntent before it was declared (openNearby moved below it) (derlenmedi)
- 1.0.262 Messages: a sent SMS is kept only when the radio really sent it (each part reports back; "Not sent" when refused), the + button icon is readable on the accent, and a thread opened from New message shows the contact name (derlenmedi)
- 1.0.263 Messages list: the back arrow is gone from the top bar (thread, New message and Settings keep it); the add button icon is readable on the accent (derlenmedi)
- 1.0.264 Lyrics notification: plain lyrics (no timestamps) are no longer dropped; their lines share the song length evenly, so the notification follows the playing position (derlenmedi)
- 1.0.265 Action centre: the Airplane mode tile switches airplane mode in place when Pulse has root or Shizuku; without them the settings page opens as before (derlenmedi)
- 1.0.266 Messages: a sent text shows in its thread at once (kept in an in-app outbox, since the system does not write sent texts when another app is the default SMS app); a refused text is removed and "Not sent" is shown (derlenmedi)
- 1.0.267 Motion cues: a tap on the thin top strip closes the open quick settings / notification panel (accessibility global action); the double tap still switches the cues off (derlenmedi)
- 1.0.268 Lyrics: plain lyrics (no timestamps) are spread over the sounding part of the song: the silence at the start and end and the gaps are left out (decoded once per song, the loud 100 ms windows); without a readable file the whole length is used. Timed lyrics not changed here (the cause of the timed case is not found yet) (derlenmedi)
- 1.0.269 Device > Security: a shield in a ring that fills with each protection on (App lock, Authenticator), "Your device is secured" in bold (or "Protection is off" when neither is on); the three tiles are bold too (derlenmedi)
- 1.0.274 RAM: the cover, video thumbnail and slide picture caches are bounded by bytes (12, 8 and 16 MB) instead of by count; a big picture now takes its real share (derlendi)
- 1.0.274 RAM: with the screen off the monitor loop (overlay, notification, alerts) reads every 5 s instead of every second; when no Pulse screen is in front the cover and video thumbnail caches are emptied (new PulseApp, onTrimMemory). Motion cues and the other services are not touched (derlendi)
- 1.0.274 Size: one APK per processor type (armeabi-v7a, arm64-v8a, x86_64) instead of one with all three; the release holds Pulse-<ver>-<abi>.apk and Pulse-<ver>-android5-<abi>.apk; the in-app updater picks the phone's own type (derlendi)
- 1.0.274 Size: 78 WebP pictures recompressed (lossless where smaller, lossy only when it looks the same); about 9.8 MB to 6.8 MB of pictures (derlendi)
- 1.0.274 Messages: sent texts move their conversation to the top and new ones are listed; the thread page has one ⋮ for pin, mute and block (block asks first) (derlendi)
- 1.0.274 Size: the per-ABI APK split is taken back (it saved no measurable size); one APK per build again, the updater takes the one release asset (derlendi)
- 1.0.274 Extra apps is always in the ⋮ menu; the "Show Extra apps in the ⋮ menu" switch is removed (derlendi)
- 1.0.274 Clock: its own launcher icon, switched on in Settings > Extra apps like the calculator (ClockLauncher alias, clock glyph); the Extra apps row for Clock follows that switch (derlendi)
- 1.0.274 Extra apps: the hide eye and the long press that hid rows are removed; a tool no longer has a hide switch (its place is still set with the chevron); the launcher icon switch of each app stays (derlendi)
- 1.0.274 Messages: a long press on a conversation picks it (no pop-up); with some picked, a bar above the list has pin, mute, archive and delete for all of them; the ⋮ of a conversation opens on the right
- 1.0.274 Keyboard: a long press on the comma opens the phone's own listening screen (see-through VoiceTypeActivity); the keyboard stays and the words are typed into the field (derlendi)
- 1.0.274 Keyboard: the comma hold (voice typing) no longer opens the ; : ' " pop-up when the finger moves (the pop-up came from the drag path) (derlendi)
- 1.0.274 Weather: when the chosen service does not answer, the other services are tried in turn (no empty card); the value cards no longer keep a blank second line under the title (derlendi)
- 1.0.274 Weather detail cards: an odd number of tiles gets the dew point, so the grid is even (derlendi)
- 1.0.274 Clock: its launcher switch now reads the real enabled state (before, the default state looked on); the ⋮ menu lists Clock only while its launcher icon is on, once (derlendi)
- 1.0.274 Clock: the purple glow at the top of the dark screens has a switch in the clock settings (Background glow); off = plain black (derlendi)
- 1.0.274 Settings > Extra apps: the Good Lock row uses the Fluent grid icon in Windows 11 (derlendi)
- 1.0.274 Device > Security: the three tiles have no box behind them (transparent, as before) (derlendi)
- 1.0.274 Device > Security: the tile labels stay on one line (they shrink to fit) (derlendi)
- 1.0.274 Device > Overview: the tabs that have a tile on the page (CPU, Memory, Thermal, Battery, Network, Storage) are not repeated in the tab row there (derlendi)
- 1.0.274 Windows 11 line icon style: the Office icon in the left menu is the line outline too, not the colour logo (derlendi)
- 1.0.274 Windows 11 settings: Mica (new) and Acrylic (new) are no longer offered in the theme list; the icon groups show their styles at once (one tap picks) (derlendi)
- 1.0.274 Clock hidden in the settings no longer shows in the ⋮ menu; Good Lock shows its Fluent grid icon in the tool rows too (derlendi)
- 1.0.274 Extra apps: a long press hides a row again (saved); an eye beside the view toggle shows the hidden rows dimmed; a first start shows everything (derlendi)
- 1.0.274 Music library: the mood mixes (Angry, Daily...) are only in the Mix maker; the Mix maker is a compact DJ-set panel: moods, sources, length and one round play button (derlendi)
- 1.0.274 Office: the title bar and ribbon colour also paints the status bar and the navigation bar (AMOLED black covers the status bar) (derlendi)
- 1.0.274 Clock: the status bar is the same black as the screen (derlendi)
- 1.0.274 Office: the hide/show arrows of the title bar are a short pull bar; keyboard clipboard: a tapped text is pasted and also put on the phone clipboard again (derlendi)
- 1.0.274 Office: a tap anywhere on the screen (not on a button or text) shows or hides the top block (derlendi)
- 1.0.274 BlackOut: when the system UI shows (Circle to Search), the Google search colours are re-applied at once, so the grey flash is shorter (derlendi)
- 1.0.274 Clock: Background glow off is pure AMOLED black (#000000), no tint (derlendi)
- 1.0.274 Keyboard gamepad: both sticks send the arrow keys (no letters); the sticks sit higher by default (derlendi)
- 1.0.274 Keyboard top bar: the gamepad key becomes the layout key (the gamepad stays in the ⋮ menu). Quick auth / saved passwords and TOTP from the keyboard not started (derlendi)
- 1.0.274 Keyboard: e-mail domain suggestions (@gmail.com ...) appear only after an "@" is typed; they no longer replace ordinary words (derlendi)
- 1.0.274 Keyboard Quick auth: a Quick auth key (⋮ menu and bar list) shows the Pulse Authenticator codes in a panel; a tap types the code; the "fingerprint first" setting is off by default (derlendi)
- 1.0.274 Clock alarm sheet: black in the dark theme; the time wheel columns take the whole panel width, so a drag anywhere on it scrolls (derlendi)
- 1.0.274 Backup leaves out the bundled ambient sounds and Winamp skins (they ship with the app); the store has no download rows for them (derlendi)
- 1.0.274 Keyboard backup: texts always; clipboard pictures only when "Clipboard pictures in the backup" is on (default off) (derlendi)
- 1.0.274 Files: a picture inside a zip opens in Pulse's own viewer, not the "Open as" chooser (derlendi)
- 1.0.274 Automatic backup: once a week (default on), the last 4 copies of the Pulse and keyboard zips (derlendi)
- 1.0.274 Keyboard backup: clipboard pictures are never in it; the switch is removed (derlendi)
- 1.0.274 Processes: CPU, Memory and Network columns wider by default (derlendi)
- 1.0.274 Processes: the name column is back to its width; CPU, Memory and Network narrower by default (derlendi)
- 1.0.274 Weather notification: one line every six hours, and at once when rain is likely in the next two hours; on by default, switch in the weather settings (derlendi)
- 1.0.274 Motion cues: a tap on the top strip collapses the quick panel with root (cmd statusbar collapse), and the accessibility action when there is no root (derlendi)
- 1.0.274 Weather notification: the next six hours with their sky icon, temperature and rain chance (derlendi)
- 1.0.274 TempMail extra app: a random disposable address from mail.tm, its inbox and mail text; its own launcher icon (Material black, glyph white), in the store and the ⋮ menu (derlendi)
- 1.0.274 Extra apps: every app stays listed (also with its launcher icon); the "Show N hidden rows" line is removed (derlendi)
- 1.0.274 Weather map extra: world map (OpenStreetMap tiles) with temperature, precipitation, wind, clouds, waves (Open-Meteo) and live aircraft (OpenSky). Ships need an AIS key and are not included (derlendi)
- 1.0.274 Weather map: rain radar (RainViewer, newest frame) and satellite (NASA GIBS, yesterday) layers; both keyless (derlendi)
- 1.0.274 Keyboard: speech typing is a panel over the keys (words come in as recognised); the layout key shows and hides the key bar; the lock key is Quick auth, the keyboard height (derlendi)
- 1.0.274 Keyboard: the layout key opens a panel with the key layouts, the key bar, the gamepad and the key set (derlendi)
- 1.0.275 Weather notification: no emoji; the hours are one under the other (time, temperature, chance of rain) (derlendi)
- 1.0.275 Weather: the back arrow at the top left is removed (the system back still leaves the page) (derlendi)
- 1.0.275 Weather: a globe button in the top bar opens the weather map (the same page as Extra apps > Weather map) (derlendi)
- 1.0.275 README: the screenshots are one block of full-height pictures (420 px, no header row); the More screenshots block is merged into it (derlendi)
- 1.0.276 Weather map: the map fills the screen, pinch to zoom and drag to move; back and the layers float over it; the empty state reads 'Couldn't load the data' and a tap retries (derlendi)
- 1.0.276 Alarms: the selection bar's buttons are white on the dark look; with the background glow off the bar is plain black with a thin edge (derlendi)
- 1.0.276 Office: the AMOLED look stays black in the Office and PDF windows, the title bar colour setting no longer paints it red (derlendi)
- 1.0.276 Office: with the AMOLED look the title bar and the grey strip under it are black too (derlendi)
- 1.0.276 Office: with the AMOLED look or the black title bar setting, the app colours (File tab, toggled buttons, splash, Excel selection accent) go grey too, not only the bar (derlendi)
- 1.0.276 Office (Word, Excel, PDF...): the top bar no longer hides into a black pill; a thin grip stays under it, pulled down from its middle to open the bar (its top edge stays fixed), pushed up to close it (derlendi)
- 1.0.276 Office: the top bar is fully hidden again; only the thin line stays (no band), tapping or pulling it brings the bar back (derlendi)
- 1.0.276 Office: the top bar handle is a down-pointing chevron instead of the thin line (derlendi)
- 1.0.276 TempMail: no crash without network or on a bad answer; TempMail is no longer listed in Extra apps (derlendi)
- 1.0.276 Weather map: no longer listed in Extra apps; it opens from the globe button on the weather page (derlendi)
- 1.0.276 TempMail: now uses the driftz temp mail API (as in the Baridmail app, MIT): a random address on one of its domains, no sign-up; network calls off the main thread and never close the app (derlendi)
- 1.0.276 Keyboard: Backspace after a comma deletes only the comma (no word is taken back); a comma behind a word is followed by a space at once, and Backspace takes that space with the comma (derlendi)
- 1.0.276 Keyboard: a second comma right behind a comma is dropped, one comma stays (derlendi)
- 1.0.276 Keyboard: the correction for the bar reads only the words of the nearby lengths instead of the whole dictionary each keystroke, so the corrected word comes sooner (derlendi)
- 1.0.276 Keyboard bar: the gamepad on the bar is replaced by the key layout; a tool is in one place only (bar or menu); the microphone (voice typing) is removed from the bar and the menu, the comma keeps it (derlendi)
- 1.0.276 Keyboard: Settings > Keyboard > 'Icons on the bar' sets how many icons the top bar has room for (default 7, the rest scroll); a letter typed in place of the space (z x c v b n m) splits the two words ('selamncanım' -> 'selam canım') (derlendi)
- 1.0.279 (sırada) FFmpeg: a tool in Pulse that runs ffmpeg on the phone's files (convert, cut, extract audio), using the ffmpeg build the user uploads (license to check first: LGPL/GPL build decides how it can ship)
- 1.0.279 (sırada) RAW editor: Gallery edit gets a RAW editor (DNG, CR2, NEF, ARW, ...); "Open with Lightroom" from the gallery opens Pulse's RAW editor; normal photos are edited in the same place (the uploaded RAW editor's code is to be checked for licence before use)
- 1.0.279 (sırada) Music: a YouTube Music and a Spotify web player inside the music player (WebView); ToS of both services limit background playback and web scraping, so this needs a decision before building
- 1.0.277 Books: a new e-book reader extra app (EPUB, TXT, PDF): a shelf with covers from the title, tap zones (left/right third turns the page, middle shows the bars), a slide-and-fade page turn, four reader themes, text size, chapters, bookmarks and book information; the place is remembered (derlendi)
- 1.0.277 Books: the bottom bar is chosen in the Options (any of nine tools), a search in the book, autoscroll with a set speed, read aloud (system speech), brightness, font choice (serif, sans, mono) and an e-ink look (black on white, no animation)(derlendi)
- 1.0.277 Private (incognito) tab in the browser: nothing kept after it is closed or switched off (cookies, storage, cache), a menu row, and a hat mark at the bottom middle; the keyboard shows the same hat mark at its bottom middle while incognito(derlendi)
- 1.0.277 RAW: Files opens camera RAW files (DNG, CR2, CR3, NEF, ARW, RAF, ORF, RW2, PEF, SRW) in the CalibRaw RAW editor when it is installed (package de.duecki.calibraw), otherwise its release page; the in-app RAW editor is still to be built(derlendi)
- 1.0.277 Media tools: a Cut tab (cut a video or audio file between two times, or keep only its sound; the streams are copied without re-encoding) (derlendi)
- 1.0.277 Web music: Spotify and YouTube Music as web players inside Pulse (their own sign-in, one view per service, kept while switching); a small Extra app, no native code, so the app does not grow (runs while the screen is in front; Google may refuse sign-in in a web view)(derlendi)
- 1.0.277 Web music is now opened from the music player's online button (Settings > 'Web müzik: YouTube Music, Spotify'), not an extra app; the version is back at 1.0.276 (the 1.0.277 bump was undone)(derlendi)
- 1.0.278 Messages: opening a conversation clears its notification and marks its messages read, so the new-message count goes(derlendi)
- 1.0.278 Keyboard bar: the icons share the bar's width evenly (the slots slider is gone); the gamepad becomes the key layout and the lock (Quick auth) is on the bar, moved once for every phone (own flag, the auto-space step had skipped it); the bar's default is layout, auth, clip, menu, translate, settings(derlendi)
- 1.0.278 Books: a reader menu (⋮), theme cards, visual and control options, customise bar buttons with order, book information, reading progress on the shelf; Books listed in Extra apps(derlendi)
- 1.0.279 Keyboard: key layout rows evenly spaced with an icon each (no gamepad or key bar there); the lock and the clipboard swapped in the bar; a comma behind a word gets its space again (the space setting had turned it off); two words run together are split before other corrections(derlendi)
- 1.0.279 Books: the empty shelf finds the books in the phone's Books and Download folders, and has an add button(derlendi)
- 1.0.279 Windows 11 extra apps list: Books uses the Fluent book glyph(derlendi)
- 1.0.279 Quick auth: every account shows the seconds left and a bar that empties with its code; the codes roll over each period. Books: the settings button at the top of the shelf(derlendi)
- 1.0.279 Buds manager: a Fluent speaker icon; tapping its name opens the Buds manager screen (also when it is not placed in the Extra apps card)(derlendi)
- 1.0.279 Good Lock: a Fluent grid icon in the Extra apps card(derlendi)
- 1.0.279 Clock: the background glow is off by default (plain black at the top); the switch in Settings turns it back on(derlendi)
- 1.0.279 Books: the bar's visual options open as a small popup above the bar (font, text size, theme, e-ink); the bars are solid so the page text no longer shows through them(derlendi)
- 1.0.279 Books: eight reader themes, a two-row bottom bar option, shelf tabs (My shelf, Recent, Favorites), cover size, favourite switch in book information(derlendi)
- 1.0.279 Books: the shelf stands on a drawn wooden background (switch "Ahşap arka plan" in the shelf options, on by default)(derlendi)
- Books reader ⋮ menu: a list dropping from the top right (Visual Options, Control Options, Miscellaneous, Themes, More Operations, Share, Book information), each opening its own full page with a back arrow (derlenmedi)
- Books reader themes: a card grid plus "Use AMOLED dark mode" and "Automatic day night switch" (the time picks the day or night theme when a book opens) (derlenmedi)
- Books reader bottom bar: a chapter slider with the share read in per cent above the buttons; bar customisation has a double-line switch on top and a check per button (derlenmedi)
- Books shelf: the ≡ button opens a drawer (Recent list, My Shelf, My Favorites, My Files; Options, About, Day/Night Theme, Exit at the bottom); the gear is gone (derlenmedi)
- Books shelf Options: a full page (My Shelf, Color, App sections) with woody background, progress, cover size, main folder, import, empty shelf, AMOLED, page animation, e-ink (derlenmedi)
- Books shelf search: a search icon filters the shelf by title (derlenmedi)
- Keyboard quick auth: each account takes a third of the panel, the text is twice the size, more accounts scroll (derlenmedi)
- Settings > General > Apps: the second Clock row (the optional tool row under the apps) is removed; Clock stays once in "Tools and life" (derlenmedi)
- Weather map: opens on the chosen place (or the phone's place) zoomed to the region, not the whole world (derlenmedi)
- Weather map: dark map from sharp tiles (@2x, sharper when zoomed in), black background, dark sea and land (derlenmedi)
- Weather map: the values are a grid over the screen, so the coast and the seas get their own points (waves included) (derlenmedi)
- Weather map: aircraft are tappable (callsign, country, height, speed, heading); the picked value shows for the grid layers (derlenmedi)
- Weather map: the last values stay on the screen with their time when a service fails; the values are kept on the phone (derlenmedi)
- Weather map: wind, waves and clouds have a colour scale with its unit at the right edge; wind and wave arrows point where the air or water goes (1.0.280 (derlendi))
- Weather map: aircraft are drawn as aircraft icons turned to their heading (1.0.281 (derlendi))
- Weather map: opens flat on the whole world; "Konumum" chip zooms to the chosen place (1.0.281 (derlendi))
- Weather settings: the OpenWeatherMap source is labelled "OpenWeatherMap" without "(your key)" (1.0.281 (derlendi))
- Keyboard quick auth: each account is as tall as its text (no longer a third of the panel), so the panel is not left empty; more accounts scroll (1.0.281 (derlendi))
- Books shelf: AMOLED mode gives the shelf a pure black background (it replaces the wooden one) (1.0.281 (derlendi))
- Keyboard quick auth: the seconds number is gone from each account; the bar under the code shows the time left (1.0.281 (derlendi))
- Video player More menu: a dark bottom panel above the progress bar with an icon per action (no radio circles), so it reads over white pictures (1.0.281 (derlendi))
- Books shelf: the cover is the first page of a PDF or the cover picture of an EPUB (saved once per book); a book without one keeps the letter card (1.0.281 (derlendi))
- Books shelf: a long press on a book asks to remove it from the shelf (the file stays on the phone) (1.0.281 (derlendi))
- Books: a book whose file was deleted outside Pulse leaves the shelf when the shelf opens; opening it removes it too (1.0.281 (derlendi))
- Messages: opening a conversation marks it read in Pulse too, so the unread count clears even when Pulse is not the default SMS app (1.0.281 (derlendi))
- Books reader: the top bar's pin is a bookmark icon; the text button opens a size slider and the font choice above the bar; the palette button opens the theme swatches with AMOLED and e-ink switches above the bar (1.0.281 (derlendi))
- Books reader: the night theme is AMOLED black; the text has a little more line and paragraph space (1.0.281 (derlendi))
- Books reader: the default text size is 22 (was 18), like the Moon Reader comparison; a size already chosen is kept (1.0.281 (derlendi))
- Pulse "Save to…" panel: the accent is white (the look's own accent), not the chosen orange; Pulse gets its accent back when the panel closes (1.0.281 (derlendi))
- Books reader: default text size 15; the bottom bar offers Moon Reader's list (Screen Orientation, Day/Night, Speak, Autoscroll, Chapters, Bookmarks, Brightness, Font Size, Search, Visual/Control/Miscellaneous, Themes, Options, Shutdown, Customize, Book information); unchecked buttons appear in the ⋮ menu; sync, dual-page, tilt and reading ruler are not in the list yet (1.0.281 (derlendi))
- Books shelf: the drawer is pure black too when AMOLED is on (1.0.281 (derlendi))
- Books shelf: two or three covers in a row, sized to the width of the screen; the cover size option is gone (1.0.281 (derlendi))
- Weather map: the base map is OpenStreetMap tiles darkened in the app (no "API key required" watermark from CARTO) (1.0.282 (derlendi))
- Books reader: the ⋮ button is always the rightmost one of the top bar; the back arrow of a menu page and the system back go straight to the book (1.0.282 (derlendi))
- Books reader ⋮ menu: Chapters, Bookmarks and Search open directly from the menu (no "More Operations" page in between) (1.0.282 (derlendi))
- Books reader: font choices are shown in their own face; the bar panel shows the bottom bar as it is now above the list (1.0.282 (derlendi))
- Books reader: the theme cards show a sample of the page (grain, the sample text and the name); the ⋮ menu lists only what is not on the bottom bar (plus Share) (1.0.282 (derlendi))
- Books shelf: EPUB covers are found also from the cover page and from pictures named cover; books that had no cover before are tried again (1.0.282 (derlendi))
- Keyboard quick auth: smaller text (name 16sp, code 24sp), taller account cards; the panel keeps the keyboard's height (1.0.282 (derlendi))
- Keyboard quick auth: the panel is as tall as the keys (four rows), so it fills the keyboard window (1.0.282 (derlendi))
- Keyboard: Gamepad is an entry of the Key layout list (the layout key opens it) (1.0.282 (derlendi))
- Gamepad: the sticks and the shoulder buttons with the size control come up under the D-pad and the face buttons; the left and right arrows are plain arrows, not emoji (1.0.282 (derlendi))
- Files grid: the rows sit one under the other (they were stacked on top of each other inside a box, which put the names on top of each other) (1.0.282 (derlendi))
- Files: Relay and LocalSend are tiles of the share sheet (first in the row) and no longer rows of Files More (1.0.282 (derlendi))
- Files: the Search tab opens the search bar at the top over every folder (1.0.282 (derlendi))
- Files: the Share tab is one page with Relay and LocalSend (each opens inside Files); the Search tab searches the whole phone from a name index kept in memory, so results come at once (1.0.282 (derlendi))
- Books: Day/Night switches the shelf and the drawer between a light and a dark look; the drawer header reads "Book reader" in a readable colour (1.0.282 (derlendi))
- Books shelf: books in a row is a setting (auto fills the width, in landscape too; or 2 to 6 chosen in Options) (1.0.282 (derlendi))
- Weather: the page retries three times and then shows the last saved forecast for the place, not an error; the clock widget redraws every minute (1.0.282 (derlendi))
- Weather: wttr.in (no key, worldwide) is a fallback service after Open-Meteo and MET Norway (1.0.282 (derlendi))
- Music: "Show lyrics on the lock screen" (on by default): the lyric notification is public, so the lock screen shows the lines; a black preview in the live lyrics settings (1.0.283 (derlendi))
- Device overview: Security is a card like the others, with SELinux, verified boot, encryption, bootloader lock and root (root gives the protected values); a tap opens the full page (1.0.283 (derlendi))
- LockArt (LSPosed module, Apache-2.0, ELCesarMaat; Spotify lyrics part adapted from LyricProvider, Apache-2.0) copied as a standalone project in extras/lockart: lock screen art, lock screen and AOD lyrics from Spotify. It is not part of Pulse's Gradle build; it builds with its own gradlew. Pulse integration (Pulse's own player lyrics into it) not done yet (1.0.283 (derlendi))
- Books shelf: covers are shown whole (fit, not cropped and zoomed); EPUB covers are also found by names with front or title (1.0.283 (derlendi))
- Books shelf: a book without a picture shows its title on the card instead of the first letter; the Alice EPUB sent has no cover image inside the file (its manifest names cover.jpg, which is missing from the zip) (1.0.283 (derlendi))
- Books shelf: a book without a cover picture (EPUB, TXT) shows its first page of text as the cover (1.0.283 (derlendi))
- Compile fix: missing Arrangement and fillMaxSize imports in LiveLyrics.kt (1.0.283 build failed on them)
- Music settings: all live lyrics options are on one page opened from one row (Diğer > Canlı sözler); the crash from the nested scroll is gone (1.0.284 (derlendi))
- Lyrics on the lock screen: the lyrics notification moves to a channel of default importance (the low one was hidden on the lock screen); the previews show a large clock, the date and the lines under it, and a dim always-on version (1.0.285 (derlendi))
- Weather map: the land is lighter; a tile not loaded yet shows part of the lower-zoom tile (no black map while zooming); values are soft blobs (heatmap, clouds as soft white); the layers are icon chips (1.0.285 (derlendi))
- Books reader: the bottom shows the page (Page x of y · Chapter n); a page slider; the words being read are highlighted; the Samsung speech engine when the phone has it, Turkish for Turkish text; books without a picture get the first page as cover (cache key v3, so earlier failures are tried again) (1.0.285 (derlendi))
- Lyrics on the lock screen: the lyrics notification is no longer marked silent (One UI and Android hide silent notifications on the lock screen); the channel has no sound, so it still makes no noise (1.0.287 (derlendi))
- Lyrics on the lock screen, rootless: while the phone is locked and a song with lyrics plays, the lyrics open full screen over the lock screen (a full-screen notification opens LyricsLockActivity; it closes after unlock or when the music stops). Needs the full-screen permission on Android 14 and up. Root version (LockArt) not done yet (1.0.287 (derlendi))
- Books reader > Customize reader bar buttons: a tap anywhere on a row ticks the button (it no longer runs the function behind the panel); Font Size shows the Aa mark as in the bar preview (1.0.287 (derlendi))
- Books reader bar: Customize reader bar buttons stays on and always last, even after other buttons are added or moved (1.0.287 (derlendi))
- Books reader: Font Size is on the bottom bar by default (existing bars get it once); the ⋮ menu lists only the tools that are not on the bar, without Visual Options (it can still be added to the bar) (1.0.287 (derlendi))
- Books reader: Double-line layout is only on the Customize reader bar buttons page; the bar can now also hold Share, Favorite and E-ink (1.0.287 (derlendi))
- Books reader themes: Space (stars over a night sky), Sakura (cherry branches with blossoms) and Sakura night; the pictures are drawn, not stored (1.0.287 (derlendi))
- Books reader: Options is no longer in the ⋮ menu (it can still be put on the bar) (1.0.287 (derlendi))
- Weather: a 0% chance of rain is shown in the hourly and daily lists (it was blank) (1.0.287 (derlendi))
- Weather map: opens centred on the chosen place or the phone's place, zoomed in; without a place the world is zoomed to fill the whole screen (no black bands) (1.0.287 (derlendi))
- Weather map aircraft: the last loaded aircraft stay on the map when it is closed and opened again, no blank wait for the first load (1.0.287 (derlendi))
- Lyrics: found lyrics are written beside the song as .lrc / .txt (no more copies in the app's data or cache); the copies already kept move beside their songs once, the rest of the old copies are removed
- Settings > About > Storage: where Pulse keeps its data and cache, what each part is for, its size, and a delete button for each (1.0.287 (derlendi))
- Lyrics: found lyrics go into the song's tag when the file takes it (MP3); otherwise a .lrc beside the song, untimed lyrics too (no .txt) (1.0.287 (derlendi))
- Lyrics on the lock screen: the full-screen lyrics view no longer takes the keyboard or touches (the PIN and unlock swipe stay usable), closes itself if it fails, and the option is off by default (1.0.288 (derlendi))
- Lyrics on the lock screen: the view is translucent with a light scrim (it turned the screen black before) (1.0.288 (derlendi))
- Lyrics on the lock screen and AOD: a strip of text at the bottom with a soft shadow and no background, over the lock screen (no full window); shows the song's name until a line is ready (1.0.288 (derlendi))
- Books reader page animation: instant by default; choices Slide, Scroll down (like a printed page feeding), Flip; tap the row to change (1.0.288 (derlendi))
- Books reader > Customize reader bar buttons: a popup over the reader; the box ticks a button for the bar, a tap on its name closes the popup and opens that button (Font Size opens the font popup) (1.0.288 (derlendi))
- Books reader ⋮ menu: each item has its icon; E-ink and Brightness are out of the menu (Brightness is in Control Options); Share moved to Book information; Share and E-ink are no longer bar buttons (1.0.288 (derlendi))
- Books reader: Bookmarks is merged into Chapters (two tabs on one page); every menu item has its icon, Font Size shows Aa (1.0.288 (derlendi))
- Books: favourites are set from the shelf (long press a book); the reader has no favourite button, no Miscellaneous page (1.0.288 (derlendi))
- Books reader: the Shutdown button is removed (1.0.288 (derlendi))
- Books reader speech: slower default (0.85) with a speed row on the Control page; the best offline voice of the engine is picked; holding the speak button or Control > Voice and speech engine opens the phone's speech settings (1.0.288 (derlendi))
- Books reader: Control page has a Speech engine row that lists the engines on the phone (Google's included); the choice applies when a book is opened (1.0.288 (derlendi))
- Books reader: the last line of a page is no longer cut off (the page measure matches the drawn spacing and keeps a margin) (1.0.288 (derlendi))
- Books: My Favorites has its icon; the drawer header is plain text; Find free books (Project Gutenberg, public domain, Turkish first) downloads into Books and adds to the shelf (1.0.288 (derlendi))
- Books find: results show covers in a grid; one tap downloads the book and opens it in the reader (1.0.288 (derlendi))
- Books find: sources can be added (a name and an address with {q}; OPDS catalogues and JSON like Gutenberg's); Gutenberg has a Turkish and an all-languages source; a long press removes an added source (1.0.288 (derlendi))
- Network type: the success check reads the phone's type names (it looked for numbers); the command also runs for each SIM id; DNS tile: no tap pop frames; dice tumbles in the air and lands; coin flip turns over in the air and lands (1.0.288 (derlendi))
- Music library: Radio category (Turkish stations from Radio Browser, search by name; a tap plays the stream) (1.0.288 (derlendi))
- Music library Radio row opens the app's existing radio screen (the RadioHub copy is removed) (1.0.288 (derlendi))
- Radio and white noise: drawn pictures instead of the grey placeholder (radio in the media notification; white noise in its notification) (1.0.288 (derlendi))
- Lyrics search: the lyrics on the phone show at once; the online catalogue is added after them (1.0.288 (derlendi))
- Extra apps: a tool set to hidden (eye icon) leaves the ⋮ menu and the Tools/Apps groups but stays listed on the Extra apps page; hiding a row there is still by long press (1.0.288 (derlendi))
- Quick panel: a Floating timer tile (shows or hides the floating timer; opens the overlay permission when missing) (1.0.288 (derlendi))
- Motion cues: a tap on the top strip closes the quick panel through the same shell route as the rest of Pulse (root or Shizuku) and the accessibility service (1.0.288 (derlendi))
- 1.0.289 Lock-screen lyrics: one line in the player's lyric font and size, the next line under it in a darker grey, on a soft dark backdrop placed at the wallpaper's peak; the frozen-line bug is fixed
- 1.0.289 Books: the Find free books search field keeps its search button on screen, and the keyboard's search key searches
- 1.0.289 Weather notification: a pop-up (heads-up) report with text sky symbols, bold figures, the next three hours, and a five-day report every twelve hours
- 1.0.289 Keyboard: sky and weather presets, many more themed presets (turtles, ships, Egyptian, sky, stars, flowers), ASCII art presets drawn whole and scaled to fit
- 1.0.289 Keyboard: a "Text and art" page with your own text in every Unicode style, and a Picture to ASCII tool (quality and invert)
- 1.0.289 Alarm: the editor starts at the phone's current time, no drag bar in AMOLED, tighter spacing; the ringing screen opens on every ring; overlay permission is requested and granted by root when available
- 1.0.289 Launcher: Pulse's accent and pop-up style no longer leak into the launcher's windows
- 1.0.289 Windows 11 look: its own icon style for the left sidebar, preview cards for background and window material, Blur removed from the window material list
- 1.0.289 Alarm appearance: live preview cards for every background style
- 1.0.290 Lock-screen lyrics: a small window with the line and the next one, instead of a full-screen view that hid the lock screen and froze; taps reach the lock screen
- 1.0.290 Lyrics notification: low importance (no pop-up), no tap target on the lock screen
- 1.0.290 Extra apps menu opens expanded; shared-link window stays open with download progress until done
- 1.0.290 Good Lock: one screen, not-installed modules grouped with picture tiles, a tap opens the module itself
- 1.0.290 Overview: phone picture and model name at the top; battery health from the measured capacity, or typed in
- 1.0.290 Playback keep-alive notification: minimum importance, no status-bar icon, no sound
- derlenmedi: Music lists stutter while scrolling (Favourites and the other song lists): cover cache is now byte-bounded instead of 30 full-size pictures, and saved covers are read at most 512 px for the rows
- derlenmedi: Lyrics search index is built in the background after every library scan (not only when the music page is open), so the first search is quick
- derlenmedi: Lyrics search reads the phone's lyrics index at once; the index is filled in the background without blocking the search (the search no longer waits for the whole library to be read)
- derlenmedi: Lock screen lyrics: two places (under the date, above the lock screen's bottom line) plus a position slider; the window follows the setting at once and the lyric line refreshes about five times a second while locked
- derlenmedi: Keyboard symbols: Hearts and ribbons first with 30 new presets; Sky and weather no longer a tab (its entries spread over the others); Text and art last. Text and art: the keys type into its box, a tap copies a style or picture to the clipboard, ASCII pictures two per line with no box
- derlenmedi: Music widget: the album cover opens the player directly (no trampoline), one request code per widget
- derlenmedi: Floating keys removed (the key button over every app, its toolkit page and its quick settings tile)
- derlenmedi: Dice and coin flip quick settings tiles turn: eight frames of a tumbling die and a turning coin, then the result
