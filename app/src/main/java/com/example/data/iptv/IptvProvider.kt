package com.example.data.iptv

import com.example.data.model.ChannelEntity
import com.example.data.model.UserSession
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

interface IptvProvider {
    suspend fun authenticate(username: String, password: String): Result<UserSession>
    suspend fun fetchChannels(session: UserSession): Result<List<ChannelEntity>>
    suspend fun fetchExternalAudio(externalAudioUrl: String): Result<List<com.example.data.model.ExternalAudioEntity>>
}

class DefaultIptvProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : IptvProvider {

    override suspend fun authenticate(username: String, password: String): Result<UserSession> {
        val trimmedUser = username.trim()
        val trimmedPass = password.trim()

        if (trimmedUser.isEmpty() || trimmedPass.isEmpty()) {
            return Result.failure(IllegalArgumentException("Username and Password cannot be empty"))
        }

        // A real management/backend API contract is required here.
        // Never manufacture a host, token, subscription, expiry or audio URL.
        return Result.failure(
            IllegalStateException(
                "Eagle Sports backend authentication is not configured. " +
                    "Configure the real backend API before enabling production login."
            )
        )
    }

    override suspend fun fetchChannels(session: UserSession): Result<List<ChannelEntity>> {
        return try {
            val xtreamUrl =
                "${session.iptvHost}/player_api.php?username=${session.username}&action=get_live_streams"

            val xtreamRequest = Request.Builder()
                .url(xtreamUrl)
                .header("User-Agent", "EagleSports/1.0 (Android TV)")
                .build()

            client.newCall(xtreamRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank() && body.trim().startsWith("[")) {
                        val channels = parseXtreamChannels(body, session)
                        if (channels.isNotEmpty()) return Result.success(channels)
                    }
                }
            }

            val m3uUrl =
                "${session.iptvHost}/get.php?username=${session.username}&type=m3u_plus&output=ts"

            val m3uRequest = Request.Builder()
                .url(m3uUrl)
                .header("User-Agent", "EagleSports/1.0 (Android TV)")
                .build()

            client.newCall(m3uRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val content = response.body?.string()
                    if (!content.isNullOrBlank()) {
                        val channels = M3uParser.parseChannels(content)
                        if (channels.isNotEmpty()) return Result.success(channels)
                    }
                }
            }

            Result.failure(IllegalStateException("No valid live channels were returned by the IPTV provider"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchExternalAudio(
        externalAudioUrl: String
    ): Result<List<com.example.data.model.ExternalAudioEntity>> {
        if (externalAudioUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("External audio M3U URL is empty"))
        }

        return try {
            val request = Request.Builder()
                .url(externalAudioUrl)
                .header("User-Agent", "EagleSports/1.0 (Audio)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("External audio request failed: HTTP ${response.code}")
                    )
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return Result.failure(IllegalStateException("External audio M3U is empty"))
                }

                val audioList = M3uParser.parseExternalAudio(body)
                if (audioList.isEmpty()) {
                    return Result.failure(
                        IllegalStateException("External audio M3U contains no valid audio streams")
                    )
                }

                Result.success(audioList)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseXtreamChannels(
        jsonStr: String,
        session: UserSession
    ): List<ChannelEntity> {
        val list = mutableListOf<ChannelEntity>()
        val jsonArray = JSONArray(jsonStr)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val streamId = obj.optInt("stream_id", i + 1)
            val name = obj.optString("name", "Channel $streamId")
            val icon = obj.optString("stream_icon").ifBlank { null }
            val category = obj.optString("category_name").ifBlank { "Sports" }
            val channelNumber = obj.optInt("num", i + 1)
            val streamUrl =
                "${session.iptvHost}/live/${session.username}/$streamId.ts"
            val tvgId = obj.optString("epg_channel_id").ifBlank { null }

            list.add(
                ChannelEntity(
                    stableId = "xtream_$streamId",
                    name = name,
                    streamUrl = streamUrl,
                    groupName = category,
                    logoUrl = icon,
                    tvgId = tvgId,
                    tvgName = name,
                    channelNumber = channelNumber,
                    isFavorite = false,
                    streamType = "ts"
                )
            )
        }

        return list
    }
}
