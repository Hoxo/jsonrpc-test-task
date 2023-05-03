package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.buffer.BufferAllocator
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.fileSize
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.asSequence

//todo escape path before action
@Singleton
class FileServiceImpl(
    private val bufferAllocator: BufferAllocator,
    @Value("\${app.rootDir}")
    private val rootDir: String,
    @Value("\${app.bufferSizeMB}")
    private val bufferSizeMB: Int,
): FileService {
    private val rootPath = Paths.get(rootDir)
    private val maxBufferSize = bufferSizeMB * 1024 * 1024

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

    override suspend fun readFile(path: String,
                                  offset: Long,
                                  toRead: Long): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (offset <= 0) {
            return@withContext Result.failure(IllegalArgumentException("offset must be positive"))
        }
        if (toRead <= 0) {
            return@withContext Result.failure(IllegalArgumentException("toRead must be positive"))
        }
        if (toRead > maxBufferSize) {
            return@withContext Result.failure(IllegalArgumentException("toRead must be less than $maxBufferSize bytes"))
        }

        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        runCatching {
            val fileSize = fullPath.fileSize()
            val expectedToRead = min(max(fileSize - offset, 0), toRead)
            if (expectedToRead == 0L) {
                return@runCatching ByteArray(0)
            }
            //todo understand, how to read file, which is bigger than memory
            //todo use buffer pool or limiting
            bufferAllocator.borrow(expectedToRead.toInt()) { buffer -> //todo toInt is not good; possible OOM
                suspendCoroutine { cont ->
                    AsynchronousFileChannel.open(fullPath, StandardOpenOption.READ).use { channel ->
                        channel.read(buffer, offset, null, object : CompletionHandler<Int, Unit?> {
                            override fun completed(result: Int, attachment: Unit?) {
                                if (result == -1) {
                                    cont.resume(ByteArray(0))
                                    return
                                }
                                cont.resume(buffer.array().copyOf(result))
                            }

                            override fun failed(exc: Throwable, attachment: Unit?) {
                                cont.resumeWithException(exc)
                            }
                        })
                    }

                }
            }
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

    private fun absolutePathFromRoot(path: String): Path = Paths.get(rootDir, path)

    private fun escapePath(path: String): String {
        return Path.of(path).normalize()
            .toString()
            .replace("..", "")
            .replace("//", "/")
    }
}