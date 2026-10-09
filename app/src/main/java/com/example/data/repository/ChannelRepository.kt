package com.example.data.repository

import com.example.data.iptv.DefaultIptvProvider
import com.example.data.iptv.IptvProvider
import com.example.data.local.ChannelDao
import com.example.data.local.ExternalAudioDao
import com.example.data.model.ChannelEntity
import com.example.data.model.ExternalAudioEntity
import com.example.data.model.UserSession
import com.example.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val message: String, val progress: Float = 0f) : SyncState()
    data class Success(val channelsCount: Int, val audioCount: Int, val warning: String? = null) : SyncState()
    data class Error(val message: String) : SyncState()
}

class ChannelRepository(
    private val channelDao: ChannelDao,
    private val audioDao: ExternalAudioDao,
    private val preferencesManager: PreferencesManager,
    private val iptvProvider: IptvProvider = DefaultIptvProvider()
) {

    val allChannels: Flow<List<ChannelEntity>> = channelDao.getAllChannels()
    val featuredChannels: Flow<List<ChannelEntity>> = channelDao.getFeaturedChannels()
    val favoriteChannels: Flow<List<ChannelEntity>> = channelDao.getFavoriteChannels()
    val allGroups: Flow<List<String>> = channelDao.getAllGroups()
    val totalCount: Flow<Int> = channelDao.getChannelCount()

    fun getChannelsByGroup(group: String): Flow<List<ChannelEntity>> {
        return if (group.equals("All", ignoreCase = true) || group.isEmpty()) {
            channelDao.getAllChannels()
        } else {
            channelDao.getChannelsByGroup(group)
        }
    }

    fun searchChannels(query: String): Flow<List<ChannelEntity>> {
        return channelDao.searchChannels(query)
    }

    suspend fun getChannelById(id: String): ChannelEntity? = withContext(Dispatchers.IO) {
        channelDao.getChannelById(id)
    }

    fun getChannelByIdFlow(id: String): Flow<ChannelEntity?> {
        return channelDao.getChannelByIdFlow(id)
    }

    suspend fun toggleFavorite(channelId: String) = withContext(Dispatchers.IO) {
        val current = channelDao.isFavorite(channelId) ?: false
        channelDao.setFavorite(channelId, !current)
    }

    fun getExternalAudioForChannelFlow(channel: ChannelEntity): Flow<List<ExternalAudioEntity>> {
        val normalized = com.example.data.iptv.M3uParser.normalizeName(channel.name)
        return audioDao.getAudioForChannelFlow(channel.stableId, channel.tvgId, normalized)
    }

    suspend fun getExternalAudioForChannel(channel: ChannelEntity): List<ExternalAudioEntity> = withContext(Dispatchers.IO) {
        val normalized = com.example.data.iptv.M3uParser.normalizeName(channel.name)
        return@withContext audioDao.getAudioForChannel(channel.stableId, channel.tvgId, normalized)
    }

    /** Returns every track from the configured external-audio M3U, without filtering by video channel name. */
    suspend fun getAllExternalAudio(): List<ExternalAudioEntity> = withContext(Dispatchers.IO) {
        return@withContext audioDao.getAllAudio().firstOrNull().orEmpty()
    }

    /**
     * Revalidates credentials against the enabled host list once when the saved host fails.
     * The activation function tries the configured hosts and returns the currently working host.
     * The optional external audio URL is preserved by the backend and remains non-blocking.
     */
    private suspend fun fetchChannelsWithHostRefresh(
        session: UserSession
    ): Pair<UserSession, Result<List<ChannelEntity>>> {
        val firstAttempt = iptvProvider.fetchChannels(session)
        if (firstAttempt.isSuccess) return session to firstAttempt

        val password = session.password
        if (session.username.isBlank() || password.isNullOrBlank()) {
            return session to firstAttempt
        }

        val refreshResult = iptvProvider.authenticate(session.username, password)
        if (refreshResult.isFailure) {
            val original = firstAttempt.exceptionOrNull()?.message ?: "فشلت مزامنة القنوات"
            val refreshError = refreshResult.exceptionOrNull()?.message ?: "فشل التحقق من الخادم"
            return session to Result.failure(
                IllegalStateException("$original؛ فشل التحقق التلقائي من الخادم: $refreshError")
            )
        }

        val refreshedSession = refreshResult.getOrThrow()
        preferencesManager.saveSession(refreshedSession)
        val retry = iptvProvider.fetchChannels(refreshedSession)
        if (retry.isFailure) {
            val error = retry.exceptionOrNull()?.message ?: "فشلت مزامنة القنوات بعد التحقق من الخادم"
            return refreshedSession to Result.failure(
                IllegalStateException("فشلت مزامنة القنوات بعد التحقق التلقائي من الخادم: $error")
            )
        }
        return refreshedSession to retry
    }

    /**
     * Refreshes the subscription configuration so an M3U URL added in the admin dashboard
     * after login is picked up without requiring the user to sign in again.
     */
    private suspend fun refreshAudioSession(savedSession: UserSession): Pair<UserSession, String?> {
        val password = savedSession.password
        if (savedSession.username.isBlank() || password.isNullOrBlank()) {
            return savedSession to "تعذّر تحديث إعدادات صوت الاشتراك: بيانات الدخول المحفوظة غير موجودة"
        }
        val refreshed = iptvProvider.authenticate(savedSession.username, password)
        return if (refreshed.isSuccess) {
            val session = refreshed.getOrThrow()
            preferencesManager.saveSession(session)
            session to null
        } else {
            savedSession to (refreshed.exceptionOrNull()?.message ?: "تعذّر تحديث إعدادات صوت الاشتراك")
        }
    }

    /**
     * Safe Atomic Synchronization:
     * 1. Download
     * 2. Parse
     * 3. Validate
     * 4. Atomic DB update (preserving user favorites)
     * If sync fails, old catalog remains intact.
     */
    suspend fun syncAll(onProgress: (SyncState) -> Unit = {}): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            onProgress(SyncState.Syncing("جارٍ الاتصال بخدمة IPTV...", 0.1f))
            val savedSession = preferencesManager.userSession.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("لا توجد جلسة مستخدم نشطة"))

            // If the locally saved host fails, revalidate once through Supabase and retry on its selected host.
            onProgress(SyncState.Syncing("جارٍ تنزيل القنوات...", 0.3f))
            val (session, channelsResult) = fetchChannelsWithHostRefresh(savedSession)
            if (channelsResult.isFailure) {
                val err = channelsResult.exceptionOrNull()?.message ?: "تعذّر جلب القنوات"
                onProgress(SyncState.Error(err))
                return@withContext Result.failure(Exception(err))
            }

            val newChannels = channelsResult.getOrThrow()
            if (newChannels.isEmpty()) {
                onProgress(SyncState.Error("قائمة القنوات التي تم تنزيلها فارغة"))
                return@withContext Result.failure(Exception("قائمة القنوات فارغة"))
            }

            onProgress(SyncState.Syncing("جارٍ تحديث قاعدة بيانات القنوات...", 0.6f))
            channelDao.atomicUpdateCatalog(newChannels)

            // Refresh config: the audio M3U may have been added/changed in the dashboard
            // after this app session was first saved.
            val (audioSession, refreshError) = refreshAudioSession(session)
            var audioCount = 0
            var audioWarning: String? = null
            val audioUrl = audioSession.externalAudioUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (audioUrl != null) {
                onProgress(SyncState.Syncing("جارٍ مزامنة التعليق الصوتي الخارجي...", 0.8f))
                val audioResult = iptvProvider.fetchExternalAudio(audioUrl)
                if (audioResult.isSuccess) {
                    val audioList = audioResult.getOrThrow()
                    audioDao.atomicUpdateExternalAudio(audioList)
                    audioCount = audioList.size
                    if (audioCount == 0) {
                        audioWarning = "قائمة الصوت الخارجي لا تحتوي على مسارات قابلة للاستخدام."
                    }
                } else {
                    audioWarning = audioResult.exceptionOrNull()?.message ?: "فشلت مزامنة الصوت الخارجي"
                }
            } else {
                audioWarning = if (refreshError != null) {
                    "رابط M3U غير متوفر في الجلسة المحفوظة، وفشل تحديث الاشتراك: $refreshError"
                } else {
                    "لم يتم ضبط رابط M3U للصوت الخارجي لهذا الاشتراك."
                }
            }

            preferencesManager.setLastSyncTime(System.currentTimeMillis())
            onProgress(SyncState.Success(newChannels.size, audioCount, audioWarning))
            Result.success(Pair(newChannels.size, audioCount))
        } catch (e: Exception) {
            onProgress(SyncState.Error(e.message ?: "فشلت المزامنة"))
            Result.failure(e)
        }
    }

    suspend fun syncChannelsOnly(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val savedSession = preferencesManager.userSession.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("لا توجد جلسة مستخدم نشطة"))
            val (session, result) = fetchChannelsWithHostRefresh(savedSession)
            if (result.isSuccess) {
                val channels = result.getOrThrow()
                channelDao.atomicUpdateCatalog(channels)
                preferencesManager.setLastSyncTime(System.currentTimeMillis())
                Result.success(channels.size)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("تعذّرت مزامنة القنوات"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncExternalAudioOnly(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val savedSession = preferencesManager.userSession.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("لا توجد جلسة مستخدم نشطة"))
            val (session, refreshError) = refreshAudioSession(savedSession)
            val audioUrl = session.externalAudioUrl?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        if (refreshError != null) {
                            "رابط M3U غير متوفر في الجلسة المحفوظة، وفشل تحديث الاشتراك: $refreshError"
                        } else {
                            "لم يتم ضبط رابط M3U للصوت الخارجي لهذا الاشتراك. احفظ الرابط في لوحة الإدارة، ثم اضغط على مزامنة التعليق الصوتي مجددًا."
                        }
                    )
                )
            val result = iptvProvider.fetchExternalAudio(audioUrl)
            if (result.isSuccess) {
                val audioList = result.getOrThrow()
                audioDao.atomicUpdateExternalAudio(audioList)
                preferencesManager.setLastSyncTime(System.currentTimeMillis())
                Result.success(audioList.size)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("تعذّرت مزامنة الصوت الخارجي"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearCatalog() = withContext(Dispatchers.IO) {
        channelDao.clearChannels()
        audioDao.clearAll()
    }
}
