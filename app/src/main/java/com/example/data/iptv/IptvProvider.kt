package com.example.data.iptv

import com.example.data.model.ChannelEntity
import com.example.data.model.UserSession
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

interface IptvProvider {
    suspend fun authenticate(host: String, username: String, password: String): Result<UserSession>
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
        private const val USER_AGENT = "EagleSports/1.0 (Android)"
    }

    override suspend fun authenticate(
        host: String,
        username: String,
        password: String
    ): Result<UserSession> {
        val enteredHost = host.trim().trimEnd('/')
        val user = username.trim()
        val pass = password

        if (enteredHost.isBlank()) {
            return Result.failure(IllegalArgumentException("يرجى إدخال عنوان السيرفر"))
        }
        if (user.isBlank() || pass.isBlank()) {
            return Result.failure(IllegalArgumentException("يرجى إدخال اسم المستخدم وكلمة المرور"))
        }

        val normalizedHost = when {
            enteredHost.startsWith("https://", ignoreCase = true) ||
                enteredHost.startsWith("http://", ignoreCase = true) -> enteredHost
            else -> "http://$enteredHost"
        }
        val encodedUser = URLEncoder.encode(user, "UTF-8")
        val encodedPass = URLEncoder.encode(pass, "UTF-8")
        val url = "$normalizedHost/player_api.php?username=$encodedUser&password=$encodedPass"

        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("فشل الاتصال بسيرفر IPTV (HTTP ${response.code})")
                    )
                }

                val json = runCatching { JSONObject(raw) }.getOrNull()
                    ?: return Result.failure(
                        IllegalStateException("السيرفر لم يُرجع بيانات Xtream صحيحة")
                    )
                val userInfo = json.optJSONObject("user_info")
                    ?: return Result.failure(
                        IllegalStateException("السيرفر لا يدعم استجابة Xtream Codes المطلوبة")
                    )

                if (userInfo.optInt("auth", 0) != 1) {
                    return Result.failure(
                        IllegalStateException("بيانات الدخول غير صحيحة أو أن الاشتراك غير صالح")
                    )
                }

                val status = userInfo.optString("status", "Active")
                if (!status.equals("Active", ignoreCase = true) &&
                    !status.equals("Trial", ignoreCase = true)
                ) {
                    return Result.failure(
                        IllegalStateException("الاشتراك غير نشط: $status")
                    )
                }

                val expDate = userInfo.optString("exp_date")
                val expiry = expDate.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?.let {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .format(java.util.Date(it * 1000L))
                    } ?: "Never"
                val serverInfo = json.optJSONObject("server_info")

                Result.success(
                    UserSession(
                        username = user,
                        password = pass,
                        token = null,
                        iptvHost = normalizedHost,
                        status = status,
                        expiryDate = expiry,
                        externalAudioUrl = null,
                        maxConnections = userInfo.optInt("max_connections", 1).coerceAtLeast(1),
                        activeConnections = userInfo.optInt("active_cons", 0).coerceAtLeast(0),
                        serverTime = serverInfo?.optString("time_now")?.takeIf { !it.isNullOrBlank() }
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "تعذّر الاتصال بسيرفر IPTV: ${e.message ?: e.javaClass.simpleName}",
                    e
                )
            )
        }
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
