package com.github.hoxo.fileservice.service

import com.github.hoxo.fileservice.model.FileChunk
import com.github.hoxo.fileservice.model.FileInfo
import kotlinx.coroutines.flow.Flow

interface FileService {
    suspend fun getInfo(path: String): FileInfo
    suspend fun list(path: String): Flow<FileInfo>
    suspend fun readFile(path: String, offset: Long, toRead: Int): FileChunk
    suspend fun createEmptyFile(path: String): FileInfo
    suspend fun createEmptyDir(path: String): FileInfo
    suspend fun delete(path: String)
    suspend fun move(path: String, newPath: String): String
    suspend fun copy(path: String, newPath: String): String
    suspend fun append(path: String, data: ByteArray): FileInfo
}