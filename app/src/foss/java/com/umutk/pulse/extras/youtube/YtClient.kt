package com.umutk.pulse.extras.youtube

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.net.HttpURLConnection
import java.net.URL

/** The network side of the extractor: plain HttpURLConnection, no extra library. */
private class YtDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val cn = URL(request.url()).openConnection() as HttpURLConnection
        cn.connectTimeout = 15_000; cn.readTimeout = 30_000
        cn.requestMethod = request.httpMethod()
        cn.instanceFollowRedirects = true
        cn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
        var cookie = ""
        request.headers().forEach { (k, v) ->
            // the encoding is left to the connection (it unpacks gzip by itself only when it asked for it)
            if (k.equals("Accept-Encoding", true)) return@forEach
            if (k.equals("Cookie", true)) { cookie = v.joinToString("; "); return@forEach }
            cn.setRequestProperty(k, v.joinToString(", "))
        }
        // the consent page of the EU is answered once for everyone
        if (request.url().contains("youtube.com")) cn.setRequestProperty("Cookie", (if (cookie.isNotEmpty()) "$cookie; " else "") + "SOCS=CAI")
        else if (cookie.isNotEmpty()) cn.setRequestProperty("Cookie", cookie)
        request.dataToSend()?.let { d -> cn.doOutput = true; cn.outputStream.use { it.write(d) } }
        val code = cn.responseCode
        if (code == 429) { cn.disconnect(); throw ReCaptchaException("reCaptcha challenge requested", request.url()) }
        val text = (if (code >= 400) cn.errorStream else cn.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val headers = HashMap<String, List<String>>()
        cn.headerFields.forEach { (k, v) -> if (k != null) headers[k] = v }
        val res = Response(code, cn.responseMessage.orEmpty(), headers, text, cn.url.toString())
        cn.disconnect()
        return res
    }
}

/** What the YouTube client of Pulse asks the extractor (NewPipeExtractor, GPL v3): a feed, a search, and the streams of one video. No account, no Shorts. */
object YtClient {
    class Item(val url: String, val title: String, val by: String, val sec: Long, val thumb: String?, val live: Boolean)
    class Streams(val title: String, val video: String?, val audio: String?, val live: Boolean)

    @Volatile private var ready = false
    private val service get() = ServiceList.YouTube

    @Synchronized private fun init() {
        if (ready) return
        NewPipe.init(YtDownloader())
        YoutubeStreamExtractor.setFetchIosClient(true)
        ready = true
    }

    private fun StreamInfoItem.toItem(): Item? {
        if (streamType != StreamType.VIDEO_STREAM && streamType != StreamType.LIVE_STREAM) return null
        // Shorts are not wanted here: they are left out of every list
        if (url.contains("/shorts/")) return null
        val thumb = thumbnails.filter { it.width > 0 }.minByOrNull { kotlin.math.abs(it.width - 480) }?.url ?: thumbnails.firstOrNull()?.url
        return Item(url, name, uploaderName.orEmpty(), if (streamType == StreamType.LIVE_STREAM) 0L else duration, thumb, streamType == StreamType.LIVE_STREAM)
    }

    /** The trending list; empty when YouTube gives none. */
    suspend fun trending(): List<Item> = withContext(Dispatchers.IO) {
        init()
        val extractor = service.kioskList.getExtractorById("Trending", null)
        KioskInfo.getInfo(service, extractor.url).relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toItem() }
    }

    /** Videos that match [q], or (with [music]) songs. */
    suspend fun search(q: String, music: Boolean): List<Item> = withContext(Dispatchers.IO) {
        init()
        val filter = if (music) YoutubeSearchQueryHandlerFactory.MUSIC_SONGS else YoutubeSearchQueryHandlerFactory.VIDEOS
        val handler = service.searchQHFactory.fromQuery(q, listOf(filter), "")
        SearchInfo.getInfo(service, handler).relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toItem() }
    }

    /** The address of the picture to watch (the best whole file with picture and sound) and of the sound alone (the best one). */
    suspend fun streams(url: String): Streams = withContext(Dispatchers.IO) {
        init()
        val info = StreamInfo.getInfo(service, url)
        val muxed = info.videoStreams.filter { it.isUrl && it.height > 0 }
        val video = (muxed.filter { it.format?.suffix == "mp4" }.ifEmpty { muxed }).maxByOrNull { it.height }?.content
        val sound = info.audioStreams.filter { it.isUrl }
        val audio = (sound.filter { it.format?.suffix == "m4a" }.ifEmpty { sound }).maxByOrNull { kotlin.math.max(it.averageBitrate, it.bitrate) }?.content
        val live = info.streamType == StreamType.LIVE_STREAM
        Streams(info.name, if (live) info.hlsUrl?.takeIf { it.isNotBlank() } else video, if (live) null else audio, live)
    }

    private val covers = LruCache<String, Bitmap>(60)

    /** A picture of a list, kept in memory. */
    suspend fun image(url: String): Bitmap? = withContext(Dispatchers.IO) {
        covers.get(url)?.let { return@withContext it }
        runCatching {
            val cn = URL(url).openConnection() as HttpURLConnection
            cn.connectTimeout = 10_000; cn.readTimeout = 15_000
            cn.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.also { covers.put(url, it) }
    }
}
