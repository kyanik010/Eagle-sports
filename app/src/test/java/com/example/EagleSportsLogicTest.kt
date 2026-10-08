package com.example

import com.example.data.iptv.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EagleSportsLogicTest {

    @Test
    fun testM3uParserChannels() {
        val sampleM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bein_1" tvg-name="beIN Sports 1" tvg-logo="https://logo.com/1.png" group-title="Sports" tvg-chno="10",beIN SPORTS 1 HD
            https://stream.server.com/live/ch1.m3u8
            #EXTINF:-1 tvg-id="ssc_1" tvg-name="SSC 1" group-title="Saudi Sports",SSC SPORTS 1
            https://stream.server.com/live/ssc1.m3u8
        """.trimIndent()

        val channels = M3uParser.parseChannels(sampleM3u)
        assertEquals(2, channels.size)
        assertEquals("beIN SPORTS 1 HD", channels[0].name)
        assertEquals("bein_1", channels[0].tvgId)
        assertEquals(10, channels[0].channelNumber)
        assertEquals("Sports", channels[0].groupName)
        assertTrue(channels[0].stableId.isNotEmpty())

        assertEquals("SSC SPORTS 1", channels[1].name)
        assertEquals("Saudi Sports", channels[1].groupName)
    }

    @Test
    fun testM3uParserExternalAudio() {
        val sampleAudioM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bein_1" audio-lang="ar" commentator="Issam Chaouali" bitrate="320 kbps",beIN Sports 1 - Issam Chaouali
            https://audio.server.com/live/ch1_issam.aac
        """.trimIndent()

        val audioList = M3uParser.parseExternalAudio(sampleAudioM3u)
        assertEquals(1, audioList.size)
        assertEquals("beIN Sports 1 - Issam Chaouali", audioList[0].title)
        assertEquals("bein_1", audioList[0].tvgId)
        assertEquals("Issam Chaouali", audioList[0].commentator)
        assertNotNull(audioList[0].streamUrl)
    }

    @Test
    fun testNameNormalization() {
        val norm1 = M3uParser.normalizeName("beIN SPORTS 1 [FHD] (Live)")
        val norm2 = M3uParser.normalizeName("beIN SPORTS 1")
        assertEquals(norm1, norm2)
    }
}
