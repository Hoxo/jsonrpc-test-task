package com.github.hoxo.fileservice.service

import com.github.hoxo.fileservice.Config
import com.github.hoxo.fileservice.buffer.BufferAllocator
import com.github.hoxo.fileservice.escapePath
import com.github.hoxo.fileservice.model.FileChunk
import com.github.hoxo.fileservice.model.FileInfo
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
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

    override suspend fun getInfo(path: String): FileInfo = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)

        val (fileName, filePath) = if (isRoot(fullPath)) {
            "" to "/"
        } else {
            fullPath.name to escapedPath
        }
        val attr = fullPath.readAttributes<BasicFileAttributes>()
        FileInfo(fileName, filePath, attr.size(), attr.isDirectory)
    }

    override suspend fun list(path: String): Flow<FileInfo> = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        Files.list(fullPath)
            .asSequence()
            .asFlow()
            .map { it to it.readAttributes<BasicFileAttributes>() }
            .map { (p, a) -> FileInfo(p.name, "/" + p.relativeTo(rootPath).toString(), a.size(), a.isDirectory) }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun readFile(
        path: String,
        offset: Long,
        toRead: Int
    ): FileChunk = withContext(Dispatchers.IO) {
        if (offset < 0) {
            throw IllegalArgumentException("offset must be non-negative")
        }
        if (toRead <= 0) {
            throw IllegalArgumentException("toRead must be positive")
        }
        if (toRead > config.maxFileChunkSize) {
            throw IllegalArgumentException("toRead must be less than ${config.maxFileChunkSize} bytes")
        }

        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)

        val attr = fullPath.readAttributes<BasicFileAttributes>()
        val fileSize = attr.size()
        val remainingBytes = max(fileSize - offset, 0)
        val expectedToRead = min(remainingBytes, toRead.toLong()).toInt()
        if (expectedToRead == 0) {
            return@withContext FileChunk(FileInfo(fullPath.name, escapedPath, fileSize, attr.isDirectory),
                offset, 0, ByteArray(0), false)
        }
        val result = ByteArray(expectedToRead)
        bufferAllocator.borrow { buffer ->
            withContext(Dispatchers.IO) {
                AsynchronousFileChannel.open(fullPath, StandardOpenOption.READ).use { channel ->
                    var position = 0
                    while (true) {
                        val read = channel.readSuspend(buffer, offset + position)
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
        FileChunk(FileInfo(fullPath.name, escapedPath, attr.size(), attr.isDirectory), offset, result.size,
            result, hasRemainingData)
    }

    override suspend fun createEmptyFile(path: String): FileInfo = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        Files.createFile(fullPath)
        FileInfo(fullPath.name, escapedPath, 0, false)
    }

    override suspend fun createEmptyDir(path: String): FileInfo = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        Files.createDirectory(fullPath)
        val fileSize = fullPath.fileSize()
        FileInfo(fullPath.name, escapedPath, fileSize, true)
    }

    override suspend fun delete(path: String): Unit = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        if (isRoot(fullPath)) {
            throw IllegalArgumentException("Cannot delete root directory")
        }
        Files.delete(fullPath)
    }

    override suspend fun move(path: String, newPath: String): String = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val escapedNewPath = escapePath(newPath)
        val fullPath = absolutePathFromRoot(escapedPath)
        val newFullPath = absolutePathFromRoot(escapedNewPath)
        if (isRoot(fullPath)) {
            throw IllegalArgumentException("Cannot move root directory")
        }
        if (isRoot(newFullPath)) {
            throw IllegalArgumentException("Cannot move to root directory")
        }
        Files.move(fullPath, newFullPath)
        escapedNewPath
    }

    override suspend fun copy(path: String, newPath: String): String = withContext(Dispatchers.IO) {
        val escapedPath = escapePath(path)
        val escapedNewPath = escapePath(newPath)
        val fullPath = absolutePathFromRoot(escapedPath)
        val newFullPath = absolutePathFromRoot(escapedNewPath)
        if (fullPath == newFullPath) {
            throw IllegalArgumentException("Cannot copy to the same path")
        }
        if (isRoot(fullPath)) {
            throw IllegalArgumentException("Cannot copy root directory")
        }
        if (isRoot(newFullPath)) {
            throw IllegalArgumentException("Cannot copy to root directory")
        }
        if (fullPath.isDirectory()) {
            copyDirectory(fullPath, newFullPath)
        } else {
            Files.copy(fullPath, newFullPath)
        }
        escapedNewPath
    }

    private fun copyDirectory(src: Path, dst: Path) {
        Files.walkFileTree(src, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetPath = dst.resolve(src.relativize(dir))
                Files.createDirectories(targetPath)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.copy(file, dst.resolve(src.relativize(file)))
                return FileVisitResult.CONTINUE
            }
        })
    }

    override suspend fun append(path: String, data: ByteArray): FileInfo = withContext(Dispatchers.IO) {
        if (data.isEmpty()) {
            throw IllegalArgumentException("data must not be empty")
        }
        val escapedPath = escapePath(path)
        val fullPath = absolutePathFromRoot(escapedPath)
        if (fullPath.isDirectory()) {
            throw FileSystemException("Cannot append to directory")
        }
        withContext(Dispatchers.IO) {
            AsynchronousFileChannel.open(fullPath, StandardOpenOption.WRITE).use { channel ->
                channel.writeSuspend(ByteBuffer.wrap(data), fullPath.fileSize())
            }
        }
        FileInfo(fullPath.name, escapedPath, fullPath.fileSize(), false)
    }

    private fun absolutePathFromRoot(path: String): Path = Paths.get(config.rootDir, path)

    private fun isRoot(path: Path): Boolean = path == rootPath
}

private suspend fun AsynchronousFileChannel.readSuspend(buffer: ByteBuffer, position: Long): Int {
    return suspendCoroutine { cont ->
        read(buffer, position, null, object : CompletionHandler<Int, Unit?> {
            override fun completed(result: Int, attachment: Unit?) {
                cont.resume(result)
            }

            override fun failed(exc: Throwable, attachment: Unit?) {
                cont.resumeWithException(exc)
            }
        })
    }
}

private suspend fun AsynchronousFileChannel.writeSuspend(buffer: ByteBuffer, position: Long): Int {
    return suspendCoroutine { cont ->
        write(buffer, position, null, object : CompletionHandler<Int, Unit?> {
            override fun completed(result: Int, attachment: Unit?) {
                cont.resume(result)
            }

            override fun failed(exc: Throwable, attachment: Unit?) {
                cont.resumeWithException(exc)
            }
        })
    }
}