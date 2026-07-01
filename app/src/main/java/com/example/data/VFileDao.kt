package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VFileDao {
    @Query("SELECT * FROM virtual_files WHERE parentId = :parentId AND channelId = :channelId AND isInTrash = 0 ORDER BY isFolder DESC, name ASC")
    fun getFilesByParentAndChannel(parentId: Long, channelId: Long): Flow<List<VFile>>

    @Query("SELECT * FROM virtual_files WHERE isFavorite = 1 AND channelId = :channelId AND isInTrash = 0 ORDER BY modifiedAt DESC")
    fun getFavoriteFilesByChannel(channelId: Long): Flow<List<VFile>>

    @Query("SELECT * FROM virtual_files WHERE isRecent = 1 AND channelId = :channelId AND isInTrash = 0 ORDER BY modifiedAt DESC LIMIT 30")
    fun getRecentFilesByChannel(channelId: Long): Flow<List<VFile>>

    @Query("SELECT * FROM virtual_files WHERE isInTrash = 1 AND channelId = :channelId ORDER BY deletedAt DESC")
    fun getTrashFilesByChannel(channelId: Long): Flow<List<VFile>>

    @Query("SELECT * FROM virtual_files WHERE name LIKE :query AND channelId = :channelId AND isInTrash = 0 ORDER BY isFolder DESC, name ASC")
    fun searchFilesByChannel(query: String, channelId: Long): Flow<List<VFile>>

    @Query("SELECT * FROM virtual_files WHERE id = :id")
    suspend fun getFileById(id: Long): VFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: VFile): Long

    @Query("DELETE FROM virtual_files WHERE id = :id")
    suspend fun deleteFileById(id: Long)

    @Query("DELETE FROM virtual_files WHERE isInTrash = 1 AND channelId = :channelId")
    suspend fun emptyTrashByChannel(channelId: Long)

    @Query("SELECT SUM(size) FROM virtual_files WHERE isFolder = 0 AND channelId = :channelId AND isInTrash = 0")
    fun getTotalStorageUsedByChannel(channelId: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM virtual_files WHERE parentId = :parentId AND name = :name AND channelId = :channelId AND isInTrash = 0")
    suspend fun countFilesWithSameName(parentId: Long, name: String, channelId: Long): Int
}
