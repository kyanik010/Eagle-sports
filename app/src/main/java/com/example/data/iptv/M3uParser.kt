package com.example.data.iptv

import com.example.data.model.ChannelEntity
import com.example.data.model.ExternalAudioEntity
import java.security.MessageDigest
import java.util.Locale
import java.util.regex.Pattern

object M3uParser {

    private val EXTINF_PATTERN = Pattern.compile(
        """#EXTINF:(?:-?\d+)\s*(?:tvg-id="([^"]*)")?\s*(?:tvg-name="([^"]*)")?\s*(?:tvg-logo="([^"]*)")?\s*(?:group-title="([^"]*)")?\s*(?:tvg-chno="([^"]*)")?,?(.*)"""
    )

    fun parseChannels(m3uContent: String): List<ChannelEntity> {
        val lines = m3uContent.lines()
        val channels = ArrayList<ChannelEntity>()

        var currentTvgId: String? = null
        var currentTvgName: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var currentChno: Int? = null
        var currentChannelName: String? = null
        var autoChno = 1

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:")) {
                // Parse attributes
                currentTvgId = extractAttribute(line, "tvg-id")
                currentTvgName = extractAttribute(line, "tvg-name")
                currentLogo = extractAttribute(line, "tvg-logo")
                currentGroup = extractAttribute(line, "group-title")
                val chnoStr = extractAttribute(line, "tvg-chno")
                currentChno = chnoStr?.toIntOrNull()

                // Extract name after comma
                val commaIdx = line.lastIndexOf(',')
                currentChannelName = if (commaIdx != -1 && commaIdx < line.length - 1) {
                    line.substring(commaIdx + 1).trim()
                } else {
                    currentTvgName ?: "Live Channel $autoChno"
                }
            } else if (!line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("rtmp://"))) {
                val streamUrl = line
                val name = currentChannelName ?: currentTvgName ?: "Live Channel $autoChno"
                val chno = currentChno ?: autoChno
                val group = currentGroup?.ifBlank { "Sports" } ?: "Sports"

                // Create a deterministic stable ID from tvg-id or normalized name + group
                val stableKey = if (!currentTvgId.isNullOrBlank()) {
                    "tvg_${currentTvgId.trim().lowercase(Locale.ROOT)}"
                } else {
                    "name_${normalizeName(name)}_${group.lowercase(Locale.ROOT)}"
                }
                val stableId = generateHashId(stableKey)

                channels.add(
                    ChannelEntity(
                        stableId = stableId,
                        name = name,
                        streamUrl = streamUrl,
                        groupName = group,
                        logoUrl = currentLogo?.ifBlank { null },
                        tvgId = currentTvgId?.ifBlank { null },
                        tvgName = currentTvgName?.ifBlank { null },
                        channelNumber = chno,
                        isFavorite = false,
                        streamType = if (streamUrl.contains(".m3u8")) "hls" else "live"
                    )
                )

                // Reset for next
                currentTvgId = null
                currentTvgName = null
                currentLogo = null
                currentGroup = null
                currentChno = null
                currentChannelName = null
                autoChno++
            }
        }
        return channels
    }

    fun parseExternalAudio(m3uContent: String): List<ExternalAudioEntity> {
        val lines = m3uContent.lines()
        val audioList = ArrayList<ExternalAudioEntity>()

        var currentTvgId: String? = null
        var currentTitle: String? = null
        var currentLang: String? = null
        var currentCommentator: String? = null
        var currentBitrate: String? = null
        var autoIndex = 1

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:")) {
                currentTvgId = extractAttribute(line, "tvg-id")
                currentLang = extractAttribute(line, "audio-lang") ?: extractAttribute(line, "lang")
                currentCommentator = extractAttribute(line, "commentator")
                currentBitrate = extractAttribute(line, "bitrate")

                val commaIdx = line.lastIndexOf(',')
                currentTitle = if (commaIdx != -1 && commaIdx < line.length - 1) {
                    line.substring(commaIdx + 1).trim()
                } else {
                    "Audio Track $autoIndex"
                }
            } else if (!line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                val streamUrl = line
                val title = currentTitle ?: "Audio Track $autoIndex"
                val normalizedName = normalizeName(title)
                val stableId = generateHashId("ext_audio_${currentTvgId ?: normalizedName}_$streamUrl")

                audioList.add(
                    ExternalAudioEntity(
                        stableId = stableId,
                        channelStableId = null,
                        tvgId = currentTvgId?.ifBlank { null },
                        normalizedName = normalizedName,
                        title = title,
                        streamUrl = streamUrl,
                        language = currentLang,
                        commentator = currentCommentator,
                        bitrate = currentBitrate
                    )
                )

                currentTvgId = null
                currentTitle = null
                currentLang = null
                currentCommentator = null
                currentBitrate = null
                autoIndex++
            }
        }
        return audioList
    }

    private fun extractAttribute(line: String, attr: String): String? {
        val prefix = "$attr=\""
        val start = line.indexOf(prefix)
        if (start != -1) {
            val valStart = start + prefix.length
            val end = line.indexOf('"', valStart)
            if (end != -1) {
                return line.substring(valStart, end)
            }
        }
        return null
    }

    fun normalizeName(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace(Regex("""\[.*?\]|\(.*?\)"""), "") // Remove brackets
            .replace(Regex("""\b(fhd|hd|sd|4k|uhd|hevc|h265|h264|live|1080p|720p)\b"""), "")
            .replace(Regex("""[^a-z0-9\u0600-\u06FF]"""), "") // keep alphanumeric and arabic characters
            .trim()
    }

    private fun generateHashId(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }
}
