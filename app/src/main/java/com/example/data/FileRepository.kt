package com.example.data

import kotlinx.coroutines.flow.Flow

class FileRepository(private val vFileDao: VFileDao) {

    fun getFilesByParent(parentId: Long, channelId: Long): Flow<List<VFile>> = 
        vFileDao.getFilesByParentAndChannel(parentId, channelId)

    fun getFavoriteFiles(channelId: Long): Flow<List<VFile>> = 
        vFileDao.getFavoriteFilesByChannel(channelId)

    fun getRecentFiles(channelId: Long): Flow<List<VFile>> = 
        vFileDao.getRecentFilesByChannel(channelId)

    fun getTrashFiles(channelId: Long): Flow<List<VFile>> =
        vFileDao.getTrashFilesByChannel(channelId)

    fun searchFiles(query: String, channelId: Long): Flow<List<VFile>> {
        val formattedQuery = "%$query%"
        return vFileDao.searchFilesByChannel(formattedQuery, channelId)
    }

    suspend fun getFileById(id: Long): VFile? = 
        vFileDao.getFileById(id)

    suspend fun insertFile(file: VFile): Long = 
        vFileDao.insertFile(file)

    suspend fun deleteFileById(id: Long) = 
        vFileDao.deleteFileById(id)

    suspend fun emptyTrashByChannel(channelId: Long) =
        vFileDao.emptyTrashByChannel(channelId)

    fun getTotalStorageUsed(channelId: Long): Flow<Long?> = 
        vFileDao.getTotalStorageUsedByChannel(channelId)

    suspend fun countFilesWithSameName(parentId: Long, name: String, channelId: Long): Int =
        vFileDao.countFilesWithSameName(parentId, name, channelId)
}
