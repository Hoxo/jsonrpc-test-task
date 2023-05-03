package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.buffer.BufferAllocator
import com.github.hoxo.fileservice.buffer.escapePath
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.fileSize
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.asSequence

@Singleton
class FileServiceImpl(
    private val bufferAllocator: BufferAllocator,
    private val config: Config,
): FileService {

    override suspend fun getInfo(path: String): Result<FileInfo> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        runCatching {
            val fileSize = fullPath.fileSize()
            FileInfo(fullPath.name, escapedPath, fileSize)
        }
    }

    override suspend fun list(path: String): Result<Flow<FileInfo>> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        runCatching {
            Files.walk(fullPath, 1)
                .asSequence()
                .asFlow()
                .map { it to Files.readAttributes(it, BasicFileAttributes::class.java) }
                .map { (p, a) -> FileInfo(p.name, p.relativeTo(fullPath).toString(), a.size()) }
                .flowOn(Dispatchers.IO)
        }
    }

    override suspend fun readFile(
        path: String,
        offset: Int,
        toRead: Int
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (offset <= 0) {
            return@withContext Result.failure(IllegalArgumentException("offset must be positive"))
        }
        if (toRead <= 0) {
            return@withContext Result.failure(IllegalArgumentException("toRead must be positive"))
        }
        if (toRead > config.maxFileChunkSize) {
            return@withContext Result.failure(
                IllegalArgumentException("toRead must be less than ${config.maxFileChunkSize} bytes"))
        }

        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        runCatching {
            val fileSize = fullPath.fileSize()
            val expectedToRead = min(max(fileSize - offset, 0), toRead.toLong()).toInt()
            if (expectedToRead == 0) {
                return@runCatching ByteArray(0)
            }
            //todo understand, how to read file, which is bigger than memory
            //todo use buffer pool or limiting
            val result = ByteArray(expectedToRead)
            bufferAllocator.borrow { buffer ->
                withContext(Dispatchers.IO) {
                    FileChannel.open(fullPath, StandardOpenOption.READ).use { channel ->
                        var position = 0
                        while (true) {
                            val read = channel.read(buffer, offset.toLong())
                            if (read == -1) {
                                break
                            }
                            val remaining = min(expectedToRead - position, read)
                            if (remaining == 0) {
                                break
                            }
                            buffer.flip()
                            buffer.get(result, position, remaining)
                            position += read
                            buffer.clear()
                        }
                    }
                }
            }
            return@runCatching result
        }
    }

    override suspend fun createEmptyFile(path: String): Result<FileInfo> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        runCatching {
            Files.createFile(fullPath)
            FileInfo(fullPath.name, escapedPath, 0)
        }
    }

    override suspend fun createEmptyDir(path: String): Result<FileInfo> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        runCatching {
            Files.createDirectory(fullPath)
            FileInfo(fullPath.name, escapedPath, 0)
        }
    }

    override suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        return@withContext runCatching {
            Files.delete(fullPath)
        }
    }

    override suspend fun move(path: String, newPath: String): Result<Path> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val escapedNewPath = escapePath(newPath)
        val fullPath = absolutePathFromRoot(escapedPath)
        val newFullPath = absolutePathFromRoot(escapedNewPath)
        return@withContext runCatching {
            Files.move(fullPath, newFullPath)
        }
    }

    override suspend fun copy(path: String, newPath: String): Result<Path> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val escapedNewPath = escapePath(newPath)
        val fullPath = absolutePathFromRoot(escapedPath)
        val newFullPath = absolutePathFromRoot(escapedNewPath)
        return@withContext runCatching {
            Files.copy(fullPath, newFullPath)
        }
    }

    override suspend fun append(path: String, data: ByteArray): Result<Path> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        return@withContext runCatching {
            Files.write(fullPath, data, StandardOpenOption.APPEND)
        }
    }

    private fun absolutePathFromRoot(path: String): Path = Paths.get(config.rootDir, path)
}