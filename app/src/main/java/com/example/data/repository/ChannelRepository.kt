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
import kotlinx.coroutines.flow.first
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
        audioDao.getAudioForChannel(channel.stableId, channel.tvgId, normalized)
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
            val session = preferencesManager.userSession.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("No active user session"))

            // Step 1: Channels sync
            onProgress(SyncState.Syncing("Downloading channels...", 0.3f))
            val channelsResult = iptvProvider.fetchChannels(session)
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

            // Step 2: External audio sync
            var audioCount = audioDao.getAllAudio().first().size
            val audioUrl = session.externalAudioUrl
            if (!audioUrl.isNullOrBlank()) {
                onProgress(SyncState.Syncing("Syncing external commentary...", 0.8f))
                val audioResult = iptvProvider.fetchExternalAudio(audioUrl)
                if (audioResult.isSuccess) {
                    val audioList = audioResult.getOrThrow()
                    // Never replace a working library with an empty/invalid result.
                    // A failed audio sync must leave the previous library untouched.
                    if (audioList.isNotEmpty()) {
                        audioDao.atomicUpdateExternalAudio(audioList)
                        audioCount = audioList.size
                    }
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
            val session = preferencesManager.userSession.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("No active user session"))
            val result = iptvProvider.fetchChannels(session)
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
                // Keep the existing library if the new feed is empty.
                if (audioList.isEmpty()) {
                    Result.failure(Exception("External audio library was empty; existing library preserved"))
                } else {
                    audioDao.atomicUpdateExternalAudio(audioList)
                    Result.success(audioList.size)
                }
            } else {
                // Existing audio remains untouched on network/parser/backend failure.
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
