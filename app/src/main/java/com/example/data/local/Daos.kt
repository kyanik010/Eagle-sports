package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.ChannelEntity
import com.example.data.model.ExternalAudioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels ORDER BY channelNumber ASC, name ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY channelNumber ASC, name ASC")
    fun getFavoriteChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE groupName = :group ORDER BY channelNumber ASC, name ASC")
    fun getChannelsByGroup(group: String): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupName FROM channels WHERE groupName != '' ORDER BY groupName ASC")
    fun getAllGroups(): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE stableId = :stableId LIMIT 1")
    suspend fun getChannelById(stableId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE stableId = :stableId LIMIT 1")
    fun getChannelByIdFlow(stableId: String): Flow<ChannelEntity?>

    @Query("SELECT isFavorite FROM channels WHERE stableId = :stableId")
    suspend fun isFavorite(stableId: String): Boolean?

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE stableId = :stableId")
    suspend fun setFavorite(stableId: String, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM channels")
    fun getChannelCount(): Flow<Int>

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' OR channelNumber LIKE '%' || :query || '%'")
    fun searchChannels(query: String): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clearChannels()

    @Query("SELECT stableId, isFavorite FROM channels WHERE isFavorite = 1")
    suspend fun getFavoriteChannelIds(): List<FavoriteIdTuple>

    /**
     * Atomic Catalog Update:
     * Preserves existing favorites even if catalog is refreshed.
     */
    @Transaction
    suspend fun atomicUpdateCatalog(newChannels: List<ChannelEntity>) {
        val existingFavs = getFavoriteChannelIds().associate { it.stableId to it.isFavorite }
        val updatedList = newChannels.map { channel ->
            if (existingFavs[channel.stableId] == true) {
                channel.copy(isFavorite = true)
            } else {
                channel
            }
        }
        clearChannels()
        insertAll(updatedList)
    }
}

data class FavoriteIdTuple(
    val stableId: String,
    val isFavorite: Boolean
)

@Dao
interface ExternalAudioDao {

    @Query("SELECT * FROM external_audio")
    fun getAllAudio(): Flow<List<ExternalAudioEntity>>

    @Query("SELECT * FROM external_audio WHERE channelStableId = :channelId OR tvgId = :tvgId OR normalizedName = :normalizedName")
    suspend fun getAudioForChannel(channelId: String?, tvgId: String?, normalizedName: String): List<ExternalAudioEntity>

    @Query("SELECT * FROM external_audio WHERE channelStableId = :channelId OR tvgId = :tvgId OR normalizedName = :normalizedName")
    fun getAudioForChannelFlow(channelId: String?, tvgId: String?, normalizedName: String): Flow<List<ExternalAudioEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(audioList: List<ExternalAudioEntity>)

    @Query("DELETE FROM external_audio")
    suspend fun clearAll()

    @Transaction
    suspend fun atomicUpdateExternalAudio(newList: List<ExternalAudioEntity>) {
        clearAll()
        insertAll(newList)
    }
}
