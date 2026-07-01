package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "virtual_files")
data class VFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val parentId: Long = 0L, // 0L represents Root folder
    val name: String,
    val isFolder: Boolean,
    val size: Long = 0L,
    val mimeType: String? = null,
    val telegramFileId: String? = null,
    val telegramFilePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isShared: Boolean = false,
    val isRecent: Boolean = true,
    val channelId: Long = 0L, // 0L for default/Saved Messages, other IDs for dynamic root channels
    val isInTrash: Boolean = false,
    val deletedAt: Long? = null
)
