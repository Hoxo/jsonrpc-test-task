package com.github.hoxo.fileservice.service

import java.nio.channels.AsynchronousFileChannel

interface FileLockRegistry {
    suspend fun <R> readLock(key: String, channel: AsynchronousFileChannel, block: suspend () -> R): R

    suspend fun <R> writeLock(key: String, channel: AsynchronousFileChannel, block: suspend () -> R): R
}