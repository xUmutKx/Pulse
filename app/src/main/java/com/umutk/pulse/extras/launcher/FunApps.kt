package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/**
 * Small joke apps for the Windows menu, all written by Pulse. Each one is a [kind] (how it works) with its own texts.
 * A text is "English|Turkish". kinds: "oracle" (a tap gives a random answer), "click" (a counter with a line for each count),
 * "scan" (a fake progress bar that ends on a result), "press" (a button that goes on to the next line).
 */
class FunApp(val id: String, val en: String, val tr: String, val kind: String, val items: List<String>)

object FunApps {
    val all: List<FunApp> = listOf(
        FunApp("magic8", "Magic 8 ball", "Sihirli 8 top", "oracle", listOf("Yes|Evet", "No|Hayır", "Ask again later|Sonra tekrar sor", "Definitely|Kesinlikle", "Not now|Şimdi değil", "Maybe|Belki", "The signs say yes|İşaretler evet diyor", "Don't count on it|Buna güvenme")),
        FunApp("fortune", "Fortune cookie", "Kurabiye falı", "oracle", listOf("Good news hides in the small things|İyi haber küçük şeylerde saklı", "You will find a sock you lost|Kaybettiğin bir çorabı bulacaksın", "A nap is the answer|Cevap bir şekerleme", "Someone is thinking of pizza|Biri pizza düşünüyor", "Today is a good day to drink water|Bugün su içmek için iyi bir gün", "Your battery will be fine|Pilin iyi olacak")),
        FunApp("dice", "Dice", "Zar", "oracle", listOf("1|1", "2|2", "3|3", "4|4", "5|5", "6|6")),
        FunApp("coin", "Coin flip", "Yazı tura", "oracle", listOf("Heads|Yazı", "Tails|Tura", "It landed on the edge|Kenarına oturdu")),
        FunApp("excuse", "Excuse maker", "Bahane makinesi", "oracle", listOf("My phone was in another room|Telefonum başka odadaydı", "The cat sat on my keyboard|Kedi klavyeme oturdu", "I was reading the terms of service|Kullanım koşullarını okuyordum", "Mercury is in retrograde|Merkür retrograd", "The Wi-Fi was thinking|Wi-Fi düşünüyordu", "I was loading|Yükleniyordum")),
        FunApp("compliment", "Compliment machine", "İltifat makinesi", "oracle", listOf("Your taste in music is excellent|Müzik zevkin harika", "You make ordinary days feel like weekends|Sıradan günleri hafta sonu gibi yapıyorsun", "Your handwriting is a work of art|El yazın bir sanat eseri", "You are the reason this phone is still fast|Bu telefonun hâlâ hızlı olmasının sebebisin", "Your smile is a software update|Gülüşün bir yazılım güncellemesi gibi")),
        FunApp("wyr", "Would you rather", "Mi yoksa", "oracle", listOf("Fly, but slowly|Uçmak, ama yavaş", "Talk to animals|Hayvanlarla konuşmak", "Never be cold|Hiç üşümemek", "Always be five minutes early|Her zaman beş dakika erken olmak", "Have no shadow|Gölgen olmasın")),
        FunApp("catname", "Name the cat", "Kedi adı", "oracle", listOf("Sir Biscuit|Sir Bisküvi", "Mr. Nap|Bay Uyku", "Waffles|Gözleme", "Admiral Meow|Amiral Miyav", "Pixel|Piksel", "Potato|Patates")),
        FunApp("weather", "Weather of the day", "Günün havası", "oracle", listOf("Sunny with a chance of nap|Güneşli, şekerleme ihtimali var", "Cloudy, with soup|Bulutlu, çorbalı", "Light rain of good ideas|Hafif iyi fikir yağmuru", "Windy, hold your hat|Rüzgarlı, şapkanı tut", "Foggy, like my plans|Sisli, planlarım gibi")),
        FunApp("horoscope", "Horoscope", "Burç yorumu", "oracle", listOf("Mercury says: drink more water|Merkür diyor ki: daha çok su iç", "Venus says: text back someone|Venüs diyor ki: birine cevap yaz", "Mars says: stretch|Mars diyor ki: esneme yap", "Saturn says: nap|Satürn diyor ki: uyu", "Jupiter says: buy snacks|Jüpiter diyor ki: atıştırmalık al")),
        FunApp("smile", "Reason to smile", "Gülme sebebi", "oracle", listOf("Your phone is charged|Telefonun şarjda", "It is almost lunch|Öğle yemeğine az kaldı", "The weekend is closer than you think|Hafta sonu düşündüğünden yakın", "Somebody likes you, probably|Biri seni seviyor, muhtemelen")),
        FunApp("snack", "Pick a snack", "Atıştırmalık seç", "oracle", listOf("Crisps|Cips", "Dark chocolate|Bitter çikolata", "A pickle|Turşu", "Cheese, obviously|Peynir, tabii ki", "Air|Hava")),
        FunApp("dinner", "Dinner decider", "Akşam yemeği", "oracle", listOf("Soup|Çorba", "Pasta|Makarna", "Pancakes for dinner|Akşam yemeğine krep", "Leftovers, it's a surprise|Artıklar, sürpriz", "Cereal|Mısır gevreği")),
        FunApp("plant", "Advice from a plant", "Bitkinin tavsiyesi", "oracle", listOf("Water me, please|Beni sula lütfen", "Stand in the sun for a while|Biraz güneşe çık", "Relax, roots take time|Rahat ol, kökler zaman ister", "Talk to me, I listen|Benimle konuş, dinliyorum", "Do nothing, grow slowly|Hiçbir şey yapma, yavaşça büyü")),
        FunApp("lottery", "Nonsense lottery", "Saçmalık piyangosu", "oracle", listOf("You won one spoon|Bir kaşık kazandın", "You won a Tuesday|Bir Salı kazandın", "You won 3 seconds of silence|3 saniyelik sessizlik kazandın", "Nothing. Good luck next time|Hiçbir şey. Bir dahaki sefere şans", "You won a second opinion|İkinci bir görüş kazandın")),
        FunApp("story", "Story starter", "Hikâye başlatıcı", "oracle", listOf("The door opened by itself, and inside was a goat in a suit.|Kapı kendi kendine açıldı, içerde takım elbiseli bir keçi vardı.", "Nobody remembered when the phone started talking.|Telefonun ne zaman konuşmaya başladığını kimse hatırlamıyordu.", "At midnight the fridge sent a letter.|Gece yarısı buzdolabı bir mektup gönderdi.")),
        FunApp("dance", "Dance move", "Dans hareketi", "oracle", listOf("The sad potato|Üzgün patates", "The slow robot|Yavaş robot", "The sudden chair|Ani sandalye", "The grandpa wave|Dede sallaması", "The invisible ladder|Görünmez merdiven")),
        FunApp("pickup", "Pickup line", "Flört cümlesi", "oracle", listOf("Are you a charger? Because I'm dying without you.|Şarj aleti misin? Çünkü sensiz ölüyorum.", "Is your name Wi-Fi? I'm feeling a connection.|Adın Wi-Fi mi? Bir bağlantı hissediyorum.", "Did it hurt when you fell from the menu?|Menüden düşerken canın acıdı mı?")),
        FunApp("poster", "Motivation poster", "Motivasyon posteri", "oracle", listOf("ONE DAY. TODAY, MAYBE.|BİR GÜN. BUGÜN, BELKİ.", "TRY. OR DON'T. CHAIRS ARE FINE.|DENE. YA DA DENEME. SANDALYELER İYİ.", "LOOK UP. THEN LOOK DOWN.|YUKARI BAK. SONRA AŞAĞI BAK.")),
        FunApp("wrong", "Wrong answers only", "Sadece yanlış cevap", "oracle", listOf("The capital of France is a banana|Fransa'nın başkenti muz", "2 + 2 is a Thursday|2 + 2 bir Perşembe", "The sun is a blue cup|Güneş mavi bir fincan", "Water is a question|Su bir sorudur")),
        FunApp("nobutton", "Do not press", "Basma", "click", listOf("I told you not to press it.|Basma dedim.", "Why would you do that?|Neden böyle yaptın?", "Okay. Keep going.|Tamam. Devam et.", "This is getting serious.|Bu ciddileşiyor.", "The button is crying now.|Düğme artık ağlıyor.", "You have 0 regrets. Impressive.|Hiç pişmanlığın yok. Etkileyici.")),
        FunApp("cookie", "Cookie clicker", "Kurabiye tıklayıcı", "click", listOf("One cookie|Bir kurabiye", "Another cookie|Bir kurabiye daha", "The cookies are multiplying|Kurabiyeler çoğalıyor", "A tiny oven has appeared|Minik bir fırın belirdi", "You are a bakery now|Artık bir fırınsın")),
        FunApp("stress", "Stress ball", "Stres topu", "click", listOf("Squeeze|Sıkıştır", "Better|Daha iyi", "Breathe in|Nefes al", "Breathe out|Nefes ver", "You are calmer than a cat|Kediden daha sakinsin")),
        FunApp("panic", "Panic button", "Panik düğmesi", "click", listOf("Nothing happened.|Hiçbir şey olmadı.", "Still nothing.|Hâlâ hiçbir şey.", "Maybe it needs more pressing.|Belki daha çok basmak gerek.", "Okay, now the phone is warm.|Tamam, şimdi telefon ısındı.")),
        FunApp("rock", "Pet rock", "Taş evcil hayvan", "click", listOf("Hi rock|Selam taş", "The rock seems happy|Taş mutlu görünüyor", "The rock learned to sit|Taş oturmayı öğrendi", "The rock wants a name|Taş bir isim istiyor", "Rock. Best friend.|Taş. En iyi arkadaş.")),
        FunApp("clap", "Clap counter", "Alkış sayacı", "click", listOf("Clap|Alkış", "Nice clap|Güzel alkış", "A crowd is forming|Kalabalık oluşuyor", "Standing ovation|Ayakta alkış", "Encore!|Bis!")),
        FunApp("trust", "Trust meter", "Güven ölçer", "click", listOf("Do you trust this?|Buna güveniyor musun?", "Hmm.|Hmm.", "Okay, a little.|Tamam, biraz.", "Now you trust it a lot.|Artık çok güveniyorsun.", "It trusts you back.|O da sana güveniyor.")),
        FunApp("blink", "Blink counter", "Göz kırpma", "click", listOf("Blink|Göz kır", "You blinked|Göz kırptın", "You are staring at the phone|Telefona bakıyorsun", "Please blink|Lütfen göz kır", "Your eyes are dry now|Gözlerin kurudu")),
        FunApp("sigh", "Sigh counter", "İç çekme", "click", listOf("Hhhh.|Hhhh.", "Another one.|Bir tane daha.", "The sighs are getting louder.|İç çekişler yükseliyor.", "The plant heard that.|Bitki bunu duydu.", "Take a break.|Mola ver.")),
        FunApp("hype", "Hype machine", "Heyecan makinesi", "click", listOf("LET'S GO|HADİ", "YES!|EVET!", "UNSTOPPABLE|DURDURULAMAZ", "THE NEIGHBORS ARE AWARE|KOMŞULAR FARKINDA", "MAXIMUM HYPE|MAKSİMUM HEYECAN")),
        FunApp("virus", "Virus scanner (totally real)", "Virüs tarayıcı (tamamen gerçek)", "scan", listOf("Scanning the cat|Kedi taranıyor", "Scanning the fridge|Buzdolabı taranıyor", "Scanning your last 10 thoughts|Son 10 düşünce taranıyor", "Result: 0 viruses, 1 cat|Sonuç: 0 virüs, 1 kedi")),
        FunApp("brain", "Brain defragmenter", "Beyin birleştirici", "scan", listOf("Moving memories to the left|Anılar sola taşınıyor", "Removing old songs|Eski şarkılar siliniyor", "Finding the password you forgot|Unuttuğun şifre aranıyor", "Done. You now remember nothing new.|Bitti. Artık yeni hiçbir şey hatırlamıyorsun.")),
        FunApp("vibe", "Vibe check", "Enerji kontrolü", "scan", listOf("Checking your vibe|Enerjin kontrol ediliyor", "Checking the playlist|Çalma listesi kontrol ediliyor", "Vibe level: good|Enerji seviyesi: iyi")),
        FunApp("snackload", "Snack loading", "Atıştırmalık yükleniyor", "scan", listOf("Opening the bag|Paket açılıyor", "Finding the last crisp|Son cips aranıyor", "Crumbs detected|Kırıntılar bulundu", "Done. It was empty.|Bitti. Boştu.")),
        FunApp("fridge", "Fridge hacker (a joke)", "Buzdolabı hack'i (şaka)", "scan", listOf("Connecting to the fridge|Buzdolabına bağlanılıyor", "Asking nicely|Kibarca soruluyor", "The fridge says: milk|Buzdolabı diyor ki: süt", "Access granted to the leftovers|Artıklara erişim verildi")),
        FunApp("mood", "Mood compiler", "Ruh hali derleyici", "scan", listOf("Compiling feelings|Duygular derleniyor", "Warnings: 3 (coffee)|Uyarılar: 3 (kahve)", "Linking the good day|İyi gün bağlanıyor", "Build succeeded: mood is okay|Derleme başarılı: ruh hali iyi")),
        FunApp("nap", "Nap installer", "Şekerleme kurucu", "scan", listOf("Downloading pillow|Yastık indiriliyor", "Installing blanket|Battaniye kuruluyor", "Restarting your eyelids|Göz kapakların yeniden başlatılıyor", "Installed. Sleep well.|Kuruldu. İyi uykular.")),
        FunApp("catdet", "Cat detector", "Kedi dedektörü", "scan", listOf("Looking under the bed|Yatağın altına bakılıyor", "Looking in the box|Kutuya bakılıyor", "Found: one cat|Bulundu: bir kedi", "The cat is already on your keyboard|Kedi zaten klavyende")),
        FunApp("socks", "Sock matcher", "Çorap eşleştirici", "scan", listOf("Sorting the drawer|Çekmece düzenleniyor", "Matching pairs|Çiftler eşleştiriliyor", "One sock is missing|Bir çorap eksik", "Still missing. Accept it.|Hâlâ eksik. Kabul et.")),
        FunApp("wifi", "Wi-Fi of the soul", "Ruhun Wi-Fi'si", "scan", listOf("Searching for signal|Sinyal aranıyor", "Signal found: Heart_5G|Sinyal bulundu: Kalp_5G", "Password: be kind|Şifre: nazik ol", "Connected to yourself|Kendine bağlandın")),
        FunApp("wisdom", "Ancient wisdom", "Eski bilgelik", "press", listOf("Patience is a phone at 3%.|Sabır, %3 pildeki telefondur.", "The best nap is the one you didn't plan.|En iyi şekerleme, planlamadığın şekerlemedir.", "Water first, then opinions.|Önce su, sonra fikirler.", "Even the sun has a snooze button.|Güneşin bile erteleme düğmesi var.")),
        FunApp("therapist", "Tiny therapist", "Minik terapist", "press", listOf("How do you feel today?|Bugün nasıl hissediyorsun?", "That sounds heavy.|Bu ağır geliyor.", "Have you tried a glass of water?|Bir bardak su denedin mi?", "We can talk about it tomorrow.|Bunu yarın konuşabiliriz.", "You are doing better than you think.|Düşündüğünden daha iyi gidiyorsun.")),
        FunApp("trailer", "Movie trailer", "Film fragmanı", "press", listOf("In a world of tiny screens...|Küçük ekranların olduğu bir dünyada...", "One phone. One charger. One chance.|Bir telefon. Bir şarj aleti. Bir şans.", "This summer, nobody sleeps.|Bu yaz kimse uyumuyor.", "Coming soon. Probably.|Yakında. Muhtemelen.")),
        FunApp("robot", "Robot diary", "Robot günlüğü", "press", listOf("Day 1: I was switched on.|1. gün: açıldım.", "Day 2: I learned the word 'nap'.|2. gün: 'şekerleme' kelimesini öğrendim.", "Day 3: Humans sleep. Interesting.|3. gün: İnsanlar uyuyor. İlginç.", "Day 4: I want a hat.|4. gün: Bir şapka istiyorum.")),
        FunApp("conspiracy", "Conspiracy board", "Komplo panosu", "press", listOf("The toaster knows.|Tost makinesi biliyor.", "Why are the socks always missing?|Çoraplar neden hep eksik?", "The clocks agree with each other.|Saatler birbiriyle hemfikir.", "Do not look at the microwave.|Mikrodalgaya bakma.")),
        FunApp("alarm", "Alarm of the day", "Günün alarmı", "press", listOf("Wake up.|Uyan.", "Wake up, the cat is hungry.|Uyan, kedi aç.", "Okay, five more minutes.|Tamam, beş dakika daha.", "It has been 40 minutes.|40 dakika geçti.")),
        FunApp("tokens", "Motivation tokens", "Motivasyon jetonları", "press", listOf("You get a token for showing up.|Gelmek için bir jeton kazandın.", "You get another for drinking water.|Su içtiğin için bir jeton daha.", "Five tokens. You may stretch.|Beş jeton. Esnemeye hakkın var.", "Redeem tokens for one nap.|Jetonları bir şekerlemeye çevir.")),
        FunApp("pirate", "Pirate captain", "Korsan kaptan", "press", listOf("Ahoy, matey!|Selam, dostum!", "The sea is calm and the Wi-Fi is weak.|Deniz sakin, Wi-Fi zayıf.", "Bring the rum. And the charger.|Rom getir. Bir de şarj aleti.", "Land ho! The kitchen!|Kara! Mutfak!")),
        FunApp("space", "Space announcer", "Uzay duyurucu", "press", listOf("This is mission control.|Burası görev kontrol.", "The astronaut is hungry.|Astronot aç.", "All systems: snacks ready.|Tüm sistemler: atıştırmalık hazır.", "Launch in ten minutes. Or after lunch.|On dakika sonra fırlatma. Ya da öğle yemeğinden sonra.")),
        FunApp("grandma", "Grandma's advice", "Babaanne öğütleri", "press", listOf("Eat something first.|Önce bir şeyler ye.", "Wear a jacket, it is cold.|Ceket giy, hava soğuk.", "Call your mother.|Annene telefon aç.", "Sit down, you look tired.|Otur, yorgun görünüyorsun.", "Have some tea, love.|Çay iç canım."))
    )
}

private fun pick(s: String, turkish: Boolean): String {
    val p = s.split("|")
    return if (turkish) p.getOrElse(1) { p[0] } else p[0]
}

/** The joke app with [id] (the Windows menu opens it as "win:fun:<id>"). */
@Composable
fun FunAppScreen(id: String) {
    val app = FunApps.all.firstOrNull { it.id == id } ?: return
    val turkish = tr("a", "b") == "b" // (tr gives the Turkish text on a Turkish phone)
    val lines = app.items.map { pick(it, turkish) }
    Box(Modifier.fillMaxSize().background(T.bg).padding(18.dp), contentAlignment = Alignment.Center) {
        when (app.kind) {
            "oracle" -> FunOracle(lines, tr("Tap", "Dokun"))
            "click" -> FunClick(lines, tr(app.en, app.tr))
            "scan" -> FunScan(lines)
            else -> FunPress(lines, tr("Next", "Sonraki"))
        }
    }
}

@Composable
private fun FunOracle(lines: List<String>, button: String) {
    var shown by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(if (shown.isEmpty()) "?" else shown, color = T.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        FunButton(button) { shown = lines.random() }
    }
}

@Composable
private fun FunClick(lines: List<String>, title: String) {
    var count by remember { mutableIntStateOf(0) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, color = T.sub, fontSize = 14.sp)
        Text("$count", color = T.text, fontSize = 64.sp, fontWeight = FontWeight.Light)
        Text(if (count == 0) "" else lines[(count - 1) % lines.size], color = T.text, fontSize = 18.sp, textAlign = TextAlign.Center)
        FunButton("+1") { count++ }
    }
}

@Composable
private fun FunScan(lines: List<String>) {
    var running by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(-1) }
    LaunchedEffect(running) {
        if (running) {
            for (i in lines.indices) { step = i; delay(900L) }
            running = false
        }
    }
    val last = lines.lastOrNull().orEmpty()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        val done = step >= lines.size - 1 && !running && step >= 0
        Text(if (done) last else if (step >= 0) lines[step.coerceIn(0, lines.size - 1)] else "", color = T.text, fontSize = 17.sp, textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth(.8f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(T.panel)) {
            val f = if (step < 0) 0f else if (done) 1f else (step + 1f) / lines.size
            Box(Modifier.fillMaxWidth(f).fillMaxSize().background(T.accent))
        }
        FunButton(if (step >= 0 && !done) tr("Scanning…", "Taranıyor…") else tr("Start", "Başlat")) {
            if (!running) { step = -1; running = true }
        }
    }
}

@Composable
private fun FunPress(lines: List<String>, button: String) {
    var at by remember { mutableIntStateOf(-1) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(if (at < 0) "…" else lines[at], color = T.text, fontSize = 20.sp, textAlign = TextAlign.Center)
        FunButton(button) { at = (at + 1) % lines.size }
    }
}

@Composable
private fun FunButton(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(T.panel).clickable { onClick() }.padding(horizontal = 26.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
