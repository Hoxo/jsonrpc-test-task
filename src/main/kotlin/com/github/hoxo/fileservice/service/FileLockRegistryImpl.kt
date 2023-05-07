package com.github.hoxo.fileservice.service

import com.github.hoxo.fileservice.Config
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.channels.FileLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val LOG = LoggerFactory.getLogger(FileLockRegistryImpl::class.java)

@Requires(property = "app.file-locking.enabled", value = "true")
@Singleton
class FileLockRegistryImpl(
    private val config: Config.FileLocking,
): FileLockRegistry {
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun <R> readLock(key: String, channel: AsynchronousFileChannel, block: suspend () -> R): R {
        val mutex = locks.getOrPut(key) { Mutex() }
        withTimeout(config.readTimeout.toMillis()) {
            mutex.lock()
        }
        try {
            LOG.debug("Acquired read mutex for $key")
            channel.lock(true).use {
                LOG.debug("Acquired shared lock for $key")
                return block()
            }
        } finally {
            mutex.unlock()
            LOG.debug("Released read mutex for $key")
        }
    }

    override suspend fun <R> writeLock(key: String, channel: AsynchronousFileChannel, block: suspend () -> R): R {
        val mutex = locks.getOrPut(key) { Mutex() }
        withTimeout(config.writeTimeout.toMillis()) {
            mutex.lock()
        }
        try {
            LOG.debug("Acquired write mutex for $key")
            channel.lock(false).use {
                LOG.debug("Acquired exclusive lock for $key")
                return block()
            }
        } finally {
            mutex.unlock()
            LOG.debug("Released write mutex for $key")
        }
    }
}

private suspend fun AsynchronousFileChannel.lock(shared: Boolean): FileLock {
    return suspendCoroutine { continuation ->
        lock(0, Long.MAX_VALUE, shared, null, object : CompletionHandler<FileLock, Unit?> {
            override fun completed(result: FileLock, attachment: Unit?) {
                continuation.resume(result)
            }

            override fun failed(exc: Throwable, attachment: Unit?) {
                continuation.resumeWithException(exc)
            }
        })
    }
}