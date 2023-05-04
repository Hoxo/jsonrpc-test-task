package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.model.FileChunk
import com.github.hoxo.fileservice.model.FileInfo
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

interface FileService {
    suspend fun getInfo(path: String): Result<FileInfo>
    suspend fun list(path: String): Result<Flow<FileInfo>>
    suspend fun readFile(path: String, offset: Long, toRead: Int): Result<FileChunk>
    suspend fun createEmptyFile(path: String): Result<FileInfo>
    suspend fun createEmptyDir(path: String): Result<FileInfo>
    suspend fun delete(path: String): Result<Unit>
    suspend fun move(path: String, newPath: String): Result<Path>
    suspend fun copy(path: String, newPath: String): Result<Path>
    suspend fun append(path: String, data: ByteArray): Result<Path>
}