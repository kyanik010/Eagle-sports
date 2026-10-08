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
    data class Success(val channelsCount: Int, val audioCount: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

class ChannelRepository(
    private val channelDao: ChannelDao,
    private val audioDao: ExternalAudioDao,
    private val preferencesManager: PreferencesManager,
    private val iptvProvider: IptvProvider = DefaultIptvProvider()
) {

    val allChannels: Flow<List<ChannelEntity>> = channelDao.getAllChannels()
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
            val original = firstAttempt.exceptionOrNull()?.message ?: "Channel sync failed"
            val refreshError = refreshResult.exceptionOrNull()?.message ?: "Host revalidation failed"
            return session to Result.failure(
                IllegalStateException("$original; automatic host revalidation failed: $refreshError")
            )
        }

        val refreshedSession = refreshResult.getOrThrow()
        preferencesManager.saveSession(refreshedSession)
        val retry = iptvProvider.fetchChannels(refreshedSession)
        if (retry.isFailure) {
            val error = retry.exceptionOrNull()?.message ?: "Channel sync failed after host revalidation"
            return refreshedSession to Result.failure(
                IllegalStateException("Channel sync failed after automatic host revalidation: $error")
            )
        }
        return refreshedSession to retry
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
            onProgress(SyncState.Syncing("Connecting to IPTV service...", 0.1f))
            val savedSession = preferencesManager.userSession.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("No active user session"))

            // If the locally saved host fails, revalidate once through Supabase and retry on its selected host.
            onProgress(SyncState.Syncing("Downloading channels...", 0.3f))
            val (session, channelsResult) = fetchChannelsWithHostRefresh(savedSession)
            if (channelsResult.isFailure) {
                val err = channelsResult.exceptionOrNull()?.message ?: "Failed to fetch channels"
                onProgress(SyncState.Error(err))
                return@withContext Result.failure(Exception(err))
            }

            val newChannels = channelsResult.getOrThrow()
            if (newChannels.isEmpty()) {
                onProgress(SyncState.Error("Downloaded channel list was empty"))
                return@withContext Result.failure(Exception("Channel list was empty"))
            }

            onProgress(SyncState.Syncing("Updating channels database...", 0.6f))
            channelDao.atomicUpdateCatalog(newChannels)

            // Step 2: External audio sync (optional; failures never block video channels).
            var audioCount = 0
            val audioUrl = session.externalAudioUrl
            if (!audioUrl.isNullOrBlank()) {
                onProgress(SyncState.Syncing("Syncing external commentary...", 0.8f))
                val audioResult = iptvProvider.fetchExternalAudio(audioUrl)
                if (audioResult.isSuccess) {
                    val audioList = audioResult.getOrThrow()
                    audioDao.atomicUpdateExternalAudio(audioList)
                    audioCount = audioList.size
                }
            }

            preferencesManager.setLastSyncTime(System.currentTimeMillis())
            onProgress(SyncState.Success(newChannels.size, audioCount))
            Result.success(Pair(newChannels.size, audioCount))
        } catch (e: Exception) {
            onProgress(SyncState.Error(e.message ?: "Sync failed"))
            Result.failure(e)
        }
    }

    suspend fun syncChannelsOnly(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val savedSession = preferencesManager.userSession.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("No active user session"))
            val (session, result) = fetchChannelsWithHostRefresh(savedSession)
            if (result.isSuccess) {
                val channels = result.getOrThrow()
                channelDao.atomicUpdateCatalog(channels)
                preferencesManager.setLastSyncTime(System.currentTimeMillis())
                Result.success(channels.size)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to sync channels"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncExternalAudioOnly(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val session = preferencesManager.userSession.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("No active user session"))
            val audioUrl = session.externalAudioUrl ?: return@withContext Result.success(0)
            val result = iptvProvider.fetchExternalAudio(audioUrl)
            if (result.isSuccess) {
                val audioList = result.getOrThrow()
                audioDao.atomicUpdateExternalAudio(audioList)
                Result.success(audioList.size)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to sync external audio"))
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
