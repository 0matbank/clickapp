package com.clickdownloader.core.extractor

import com.fasterxml.jackson.databind.ObjectMapper
import com.clickdownloader.core.model.LiveStatus
import com.clickdownloader.core.model.StreamProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpJsonNormalizerTest {
    @Test
    fun `playlist entries retain selection identity order and source URL`() {
        val json = ObjectMapper().readTree(
            """{"_type":"playlist","id":"p1","title":"List","webpage_url":"https://source.test/list","entries":[{"id":"a","title":"One","webpage_url":"https://source.test/a","duration":4},{"id":"b","title":"Two","webpage_url":"https://source.test/b"}]}""",
        )

        val result = YtDlpJsonNormalizer.normalize(json, "https://source.test/list")

        assertTrue(result.isPlaylist)
        assertEquals(listOf("a", "b"), result.playlistItems.map { it.id })
        assertEquals(listOf(0, 1), result.playlistItems.map { it.position })
        assertEquals(4_000L, result.playlistItems.first().durationMillis)
    }

    @Test
    fun `normalizer preserves every source format and technical field`() {
        val json = ObjectMapper().readTree(
            """
            {
              "id":"abc", "title":"Source title", "webpage_url":"https://source.test/watch/abc",
              "extractor_key":"Fixture", "duration":12.5, "live_status":"was_live",
              "thumbnails":[{"url":"https://img.test/a.jpg"}],
              "formats":[
                {"format_id":"audio-251","ext":"webm","protocol":"https","url":"https://cdn.test/a","vcodec":"none","acodec":"opus","abr":160,"asr":48000,"audio_channels":2,"language":"bn","filesize":1234},
                {"format_id":"video-401","format_note":"2160p HDR","ext":"mp4","protocol":"http_dash_segments","url":"https://cdn.test/v.mpd","width":3840,"height":2160,"fps":60,"vcodec":"av01.0.12M.10","acodec":"none","vbr":12000,"dynamic_range":"HDR10","bit_depth":10,"filesize_approx":9999,"has_drm":false},
                {"format_id":"blocked","protocol":"m3u8_native","url":"https://cdn.test/drm.m3u8","vcodec":"avc1","acodec":"mp4a","has_drm":true}
              ],
              "subtitles":{"bn":[{"ext":"vtt","url":"https://sub.test/bn.vtt","name":"Bangla"}]},
              "automatic_captions":{"en":[{"ext":"srv3","url":"https://sub.test/en"}]}
            }
            """.trimIndent(),
        )

        val result = YtDlpJsonNormalizer.normalize(json, "https://input.test")

        assertEquals(3, result.formats.size)
        assertEquals(LiveStatus.WAS_LIVE, result.metadata.liveStatus)
        assertTrue(result.formats[0].isAudioOnly)
        assertEquals(160_000L, result.formats[0].audioBitrate)
        assertEquals(StreamProtocol.DASH, result.formats[1].protocol)
        assertEquals("HDR10", result.formats[1].dynamicRange)
        assertEquals(10, result.formats[1].bitDepth)
        assertTrue(result.formats[2].drmProtected)
        assertFalse(result.formats[2].isProgressive)
        assertEquals(2, result.subtitles.size)
        assertTrue(result.subtitles.last().isAutomatic)
    }
}
