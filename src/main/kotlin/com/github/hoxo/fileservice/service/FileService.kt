package com.github.hoxo.fileservice.service

import com.github.hoxo.fileservice.model.FileChunk
import com.github.hoxo.fileservice.model.FileInfo
import kotlinx.coroutines.flow.Flow

interface FileService {
    suspend fun getInfo(path: String): Result<FileInfo>
    suspend fun list(path: String): Result<Flow<FileInfo>>
    suspend fun readFile(path: String, offset: Long, toRead: Int): Result<FileChunk>
    suspend fun createEmptyFile(path: String): Result<FileInfo>
    suspend fun createEmptyDir(path: String): Result<FileInfo>
    suspend fun delete(path: String): Result<Unit>
    suspend fun move(path: String, newPath: String): Result<String>
    suspend fun copy(path: String, newPath: String): Result<String>
    suspend fun append(path: String, data: ByteArray): Result<FileInfo>
}