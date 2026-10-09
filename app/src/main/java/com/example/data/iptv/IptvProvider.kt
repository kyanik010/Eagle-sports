package com.example.data.iptv

import com.example.data.model.ChannelEntity
import com.example.data.model.UserSession
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
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

    companion object {
        private const val ACTIVATION_URL =
            "https://quaftlmuobshbnlhctmf.supabase.co/functions/v1/device-activation"
        // Supabase publishable key is intended for client applications.
        private const val SUPABASE_PUBLISHABLE_KEY =
            "sb_publishable_6mdnO-ezptIZzRKNfEbzaA_e9B-ulL5"
        private const val USER_AGENT = "EagleSports/1.0 (Android)"
    }

    override suspend fun authenticate(username: String, password: String): Result<UserSession> {
        val user = username.trim()
        val pass = password

        if (user.isEmpty() || pass.isEmpty()) {
            return Result.failure(IllegalArgumentException("Username and Password cannot be empty"))
        }

        return try {
            val requestBody = JSONObject()
                .put("username", user)
                .put("password", pass)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(ACTIVATION_URL)
                .post(requestBody)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("apikey", SUPABASE_PUBLISHABLE_KEY)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()

                if (!response.isSuccessful) {
                    val error = json?.optString("error").orEmpty()
                    return Result.failure(
                        IllegalStateException(
                            "device-activation HTTP ${response.code}: " +
                                error.ifBlank { raw.take(200).ifBlank { "empty response" } }
                        )
                    )
                }

                if (json == null) {
                    return Result.failure(
                        IllegalStateException("device-activation returned invalid JSON")
                    )
                }

                val status = json.optString("status", "unknown")
                val activated = json.optBoolean("activated", false)
                val config = json.optJSONObject("config")

                if (!activated || config == null) {
                    return Result.failure(
                        IllegalStateException("device-activation: ${statusMessage(status)}")
                    )
                }

                val video = config.optJSONObject("video")
                    ?: return Result.failure(
                        IllegalStateException("device-activation: missing video configuration")
                    )

                val host = video.optString("server_url").trim().trimEnd('/')
                val resolvedUser = video.optString("username").trim()
                val resolvedPass = video.optString("password")

                if (host.isBlank() || resolvedUser.isBlank() || resolvedPass.isBlank()) {
                    return Result.failure(
                        IllegalStateException("device-activation: incomplete video configuration")
                    )
                }

                val audio = config.optJSONObject("audio")
                val audioUrl = audio?.optString("m3u_url")?.takeIf { it.isNotBlank() }

                Result.success(
                    UserSession(
                        username = resolvedUser,
                        password = resolvedPass,
                        token = json.optString("device_id").takeIf { it.isNotBlank() },
                        iptvHost = host,
                        status = status,
                        expiryDate = json.optString("expires_at").ifBlank { "Never" },
                        externalAudioUrl = audioUrl
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "device-activation connection failed: ${e.message ?: e.javaClass.simpleName}",
                    e
                )
            )
        }
    }

    private fun statusMessage(status: String): String = when (status) {
        "pending_configuration" -> "pending_configuration (Host is not configured/enabled)"
        "host_disabled" -> "host_disabled (assigned Host is disabled or missing)"
        "expired" -> "expired (subscription/device has expired)"
        "ambiguous_credentials" -> "ambiguous_credentials (multiple matching accounts)"
        "suspended", "disabled", "blocked" -> "${status} (device is not active)"
        "unknown" -> "unknown (backend did not return a status)"
        else -> status
    }

    override suspend fun fetchChannels(session: UserSession): Result<List<ChannelEntity>> {
        return try {
            val user = URLEncoder.encode(session.username, "UTF-8")
            val pass = URLEncoder.encode(session.password.orEmpty(), "UTF-8")
            val host = session.iptvHost.trimEnd('/')

            if (pass.isBlank()) {
                return Result.failure(IllegalStateException("Missing IPTV password in active session"))
            }

            val url =
                "${host}/player_api.php?username=${user}&password=${pass}&action=get_live_streams"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("IPTV API HTTP ${response.code}")
                    )
                }

                val body = response.body?.string().orEmpty()
                if (!body.trim().startsWith("[")) {
                    return Result.failure(
                        IllegalStateException("IPTV API returned an invalid channel list")
                    )
                }

                // Xtream live streams often contain only category_id, not category_name.
                // Fetch the subscription's real category list and map IDs to display names.
                val categories = fetchLiveCategories(host, user, pass)
                val channels = parseXtreamChannels(body, session, categories)
                if (channels.isEmpty()) {
                    Result.failure(IllegalStateException("IPTV API returned 0 live channels"))
                } else {
                    Result.success(channels)
                }
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "IPTV channel sync failed: ${e.message ?: e.javaClass.simpleName}",
                    e
                )
            )
        }
    }

    override suspend fun fetchExternalAudio(
        externalAudioUrl: String
    ): Result<List<com.example.data.model.ExternalAudioEntity>> {
        return try {
            val request = Request.Builder()
                .url(externalAudioUrl)
                .header("User-Agent", "$USER_AGENT (Audio)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("External audio HTTP ${response.code}")
                    )
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return Result.failure(IllegalStateException("External audio playlist is empty"))
                }

                val audioList = M3uParser.parseExternalAudio(body)
                if (audioList.isEmpty()) {
                    Result.failure(
                        IllegalStateException("External audio playlist contains 0 tracks")
                    )
                } else {
                    Result.success(audioList)
                }
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "External audio sync failed: ${e.message ?: e.javaClass.simpleName}",
                    e
                )
            )
        }
    }

    private fun fetchLiveCategories(
        host: String,
        encodedUser: String,
        encodedPass: String
    ): Map<String, String> {
        val url = "$host/player_api.php?username=$encodedUser&password=$encodedPass&action=get_live_categories"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyMap<String, String>()
                val body = response.body?.string().orEmpty()
                if (!body.trim().startsWith("[")) return@use emptyMap<String, String>()
                val categories = JSONArray(body)
                buildMap {
                    for (i in 0 until categories.length()) {
                        val item = categories.optJSONObject(i) ?: continue
                        val id = item.optString("category_id").trim()
                        val name = item.optString("category_name").trim()
                        if (id.isNotBlank() && name.isNotBlank() && name != "null") {
                            put(id, name)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Keep channel synchronization usable if the provider does not expose categories.
            emptyMap()
        }
    }

    private fun parseXtreamChannels(
        jsonStr: String,
        session: UserSession,
        categories: Map<String, String>
    ): List<ChannelEntity> {
        val list = mutableListOf<ChannelEntity>()
        val jsonArray = JSONArray(jsonStr)
        val user = URLEncoder.encode(session.username, "UTF-8")
        val pass = URLEncoder.encode(session.password.orEmpty(), "UTF-8")
        val host = session.iptvHost.trimEnd('/')

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val streamId = obj.optInt("stream_id", i + 1)
            val name = obj.optString("name", "Channel ${streamId}")
            val icon = obj.optString("stream_icon").ifBlank { null }
            val categoryId = obj.optString("category_id").trim()
            val embeddedCategory = obj.optString("category_name").trim()
                .takeIf { it.isNotBlank() && it != "null" }
            val category = categories[categoryId]
                ?: embeddedCategory
                ?: categoryId.takeIf { it.isNotBlank() && it != "null" }?.let { "تصنيف $it" }
                ?: "غير مصنف"
            val channelNumber = obj.optInt("num", i + 1)
            val streamUrl = "${host}/live/${user}/${pass}/${streamId}.ts"
            val tvgId = obj.optString("epg_channel_id").ifBlank { null }

            list.add(
                ChannelEntity(
                    stableId = "xtream_${streamId}",
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
