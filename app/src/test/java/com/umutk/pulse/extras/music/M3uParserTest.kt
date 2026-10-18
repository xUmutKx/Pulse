package com.umutk.pulse.extras.music

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {

    @Test
    fun parsesExtinfAndResolvesRelativePaths() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:215,Artist - Song\r\n..\\music\\a.mp3\r\nb.mp3\r\n#EXTINF:-1,\r\n/abs/c.mp3\r\n"
        assertEquals(
            listOf(
                Track("/sdcard/music/a.mp3", "Artist - Song", 215),
                Track("/sdcard/lists/b.mp3"),
                Track("/abs/c.mp3", null, -1),
            ),
            M3uParser.parse(text, baseDir = "/sdcard/lists"),
        )
    }

    @Test
    fun titleKeepsCommasAfterTheFirstOne() {
        assertEquals(listOf(Track("a.mp3", "Artist, Song", 215)), M3uParser.parse("#EXTINF:215,Artist, Song\na.mp3"))
    }

    @Test
    fun extinfWithoutPathIsDroppedAndLaterExtinfWins() {
        val tracks = M3uParser.parse("#EXTINF:10,X\n#EXTINF:20,Y\nb.mp3\n#EXTINF:5,Z\n")
        assertEquals(listOf(Track("b.mp3", "Y", 20)), tracks)
    }

    @Test
    fun unknownDirectivesAreIgnored() {
        assertEquals(listOf(Track("a.mp3")), M3uParser.parse("#EXTM3U\n#EXTGRP:x\n#PLAYLIST:foo\na.mp3\n"))
    }

    @Test
    fun windowsDrivePathsKeepTheirRoot() {
        assertEquals(listOf(Track("C:/Music/a.mp3")), M3uParser.parse("C:\\Music\\a.mp3", baseDir = "/x"))
    }

    @Test
    fun urlBaseIsJoinedWithoutNormalizing() {
        assertEquals(
            listOf(Track("https://h.example/music/x.mp3")),
            M3uParser.parse("x.mp3", baseDir = "https://h.example/music/"),
        )
    }

    @Test
    fun writeRelativeThenParseRoundTrips() {
        val tracks = listOf(Track("/sdcard/music/rock/a.mp3", "A", 100), Track("/other/b.mp3"))
        val text = M3uParser.write(tracks, baseDir = "/sdcard/music")
        assertEquals("#EXTM3U\n#EXTINF:100,A\nrock/a.mp3\n#EXTINF:-1,\n/other/b.mp3\n", text)
        assertEquals(listOf("/sdcard/music/rock/a.mp3", "/other/b.mp3"), M3uParser.parse(text, "/sdcard/music").map { it.path })
    }

    @Test
    fun writeKeepsPathsOutsideBaseFolderAbsolute() {
        // "/sdcard/musicbox" starts with "/sdcard/music" but is not inside it.
        assertEquals(
            "#EXTM3U\n#EXTINF:-1,\n/sdcard/musicbox/a.mp3\n",
            M3uParser.write(listOf(Track("/sdcard/musicbox/a.mp3")), baseDir = "/sdcard/music"),
        )
    }

    @Test
    fun writeFlattensNewlinesInTitleAndPath() {
        assertEquals("#EXTM3U\n#EXTINF:5,x y\na.mp3\n", M3uParser.write(listOf(Track("a.mp3", "x\ny", 5))))
    }

    @Test
    fun emptyQueueWritesHeaderOnly() {
        assertEquals("#EXTM3U\n", M3uParser.write(emptyList()))
        assertEquals(emptyList<Track>(), M3uParser.parse("#EXTM3U\n"))
    }
}
