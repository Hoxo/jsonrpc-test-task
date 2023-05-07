package com.github.hoxo.fileservice.service

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.nio.channels.AsynchronousFileChannel

@Requires(property = "app.file-locking.enabled", value = "false")
@Singleton
class NoOpFileLockRegistry: FileLockRegistry {
    override suspend fun <R> readLock(key: String, channel: AsynchronousFileChannel, block: suspend () -> R): R {
        return block()
    }

    override suspend fun <R> writeLock(key: String, channel: AsynchronousFileChannel, block: suspend () -> R): R {
        return block()
    }
}