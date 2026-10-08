package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "channels", indices = [Index(value = ["groupName"]), Index(value = ["isFavorite"]), Index(value = ["channelNumber"])])
data class ChannelEntity(
    @PrimaryKey val stableId: String,
    val name: String,
    val streamUrl: String,
    val groupName: String = "All",
    val logoUrl: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val channelNumber: Int = 0,
    val isFavorite: Boolean = false,
    val streamType: String = "live",
    val epgCurrentTitle: String? = null,
    val epgNextTitle: String? = null,
    val updatedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "external_audio", indices = [Index(value = ["channelStableId"]), Index(value = ["tvgId"]), Index(value = ["normalizedName"])])
data class ExternalAudioEntity(
    @PrimaryKey val stableId: String,
    val channelStableId: String? = null,
    val tvgId: String? = null,
    val normalizedName: String,
    val title: String,
    val streamUrl: String,
    val language: String? = null,
    val commentator: String? = null,
    val bitrate: String? = null,
    val updatedTimestamp: Long = System.currentTimeMillis()
)

data class EpgProgram(
    val title: String,
    val description: String? = null,
    val startTime: String? = null,
    val endTime: String? = null
)

data class UserSession(
    val username: String,
    val iptvPassword: String,
    val token: String? = null,
    val iptvHost: String,
    val status: String = "Active",
    val expiryDate: String = "Never",
    val externalAudioUrl: String? = null,
    val maxConnections: Int = 1,
    val activeConnections: Int = 1,
    val serverTime: String? = null
)
