package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.buffer.BufferAllocator
import com.github.hoxo.fileservice.buffer.escapePath
import com.github.hoxo.fileservice.model.FileChunk
import com.github.hoxo.fileservice.model.FileInfo
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
import kotlin.io.path.*
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.asSequence

@Singleton
class FileServiceImpl(
    private val bufferAllocator: BufferAllocator,
    private val config: Config,
): FileService {
    private val rootPath = Path.of(config.rootDir)

    init {
        if (!rootPath.isDirectory()) {
            throw IllegalArgumentException("Root dir ${config.rootDir} is not a directory")
        }
    }

    override suspend fun getInfo(path: String): Result<FileInfo> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)

        val (fileName, filePath) = if (isRoot(fullPath)) {
            "" to "/"
        } else {
            fullPath.name to escapedPath
        }
        runCatching {
            val attr = fullPath.readAttributes<BasicFileAttributes>()
            FileInfo(fileName, filePath, attr.size(), attr.isDirectory)
        }
    }

    override suspend fun list(path: String): Result<Flow<FileInfo>> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        runCatching {
            Files.list(fullPath)
                .asSequence()
                .asFlow()
                .map { it to Files.readAttributes(it, BasicFileAttributes::class.java) }
                .map { (p, a) -> FileInfo(p.name, "/" + p.relativeTo(fullPath).toString(), a.size(), a.isDirectory) }
                .flowOn(Dispatchers.IO)
        }
    }

    override suspend fun readFile(
        path: String,
        offset: Long,
        toRead: Int
    ): Result<FileChunk> = withContext(Dispatchers.IO) {
        if (offset < 0) {
            return@withContext Result.failure(IllegalArgumentException("offset must be non-negative"))
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
            val attr = fullPath.readAttributes<BasicFileAttributes>()
            val fileSize = attr.size()
            val remainingBytes = max(fileSize - offset, 0)
            val expectedToRead = min(remainingBytes, toRead.toLong()).toInt()
            if (expectedToRead == 0) {
                return@runCatching FileChunk(FileInfo(fullPath.name, escapedPath, fileSize, attr.isDirectory),
                    offset, 0, ByteArray(0), false)
            }
            //todo use buffer pool or limiting
            val result = ByteArray(expectedToRead)
            bufferAllocator.borrow { buffer ->
                withContext(Dispatchers.IO) {
                    FileChannel.open(fullPath, StandardOpenOption.READ).use { channel ->
                        var position = 0
                        while (true) {
                            val read = channel.read(buffer, offset + position)
                            if (read == -1) {
                                break
                            }
                            val rest = max(expectedToRead - position, 0)
                            val remaining = min(rest, read)
                            if (remaining == 0) {
                                break
                            }
                            buffer.flip()
                            buffer.get(result, position, remaining)
                            position += remaining
                            buffer.clear()
                            if (position == expectedToRead) {
                                break
                            }
                        }
                    }
                }
            }
            val hasRemainingData = expectedToRead < remainingBytes
            FileChunk(FileInfo(fullPath.name, escapedPath, attr.size(), attr.isDirectory), offset, result.size.toLong(),
                result, hasRemainingData)
        }
    }

    override suspend fun createEmptyFile(path: String): Result<FileInfo> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        runCatching {
            Files.createFile(fullPath)
            FileInfo(fullPath.name, escapedPath, 0, false)
        }
    }

    override suspend fun createEmptyDir(path: String): Result<FileInfo> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        runCatching {
            Files.createDirectory(fullPath)
            val fileSize = fullPath.fileSize()
            FileInfo(fullPath.name, escapedPath, fileSize, true)
        }
    }

    override suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        return@withContext runCatching {
            Files.delete(fullPath)
        }
    }

    override suspend fun move(path: String, newPath: String): Result<String> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val escapedNewPath = escapePath(newPath)
        val fullPath = absolutePathFromRoot(escapedPath)
        val newFullPath = absolutePathFromRoot(escapedNewPath)
        return@withContext runCatching {
            Files.move(fullPath, newFullPath)
            escapedNewPath
        }
    }

    override suspend fun copy(path: String, newPath: String): Result<String> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val escapedNewPath = escapePath(newPath)
        val fullPath = absolutePathFromRoot(escapedPath)
        val newFullPath = absolutePathFromRoot(escapedNewPath)
        return@withContext runCatching {
            Files.copy(fullPath, newFullPath)
            escapedNewPath
        }
    }

    override suspend fun append(path: String, data: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        return@withContext runCatching {
            Files.write(fullPath, data, StandardOpenOption.APPEND)
            escapedPath
        }
    }

    private fun absolutePathFromRoot(path: String): Path = Paths.get(config.rootDir, path)

    private fun isRoot(path: Path): Boolean = path == rootPath
}