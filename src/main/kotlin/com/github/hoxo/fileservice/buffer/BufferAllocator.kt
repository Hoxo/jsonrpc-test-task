package com.github.hoxo.fileservice.buffer

import java.nio.ByteBuffer

interface BufferAllocator {
    suspend fun allocate(size: Int): ByteBuffer
    suspend fun <T> borrow(size: Int, f: suspend (ByteBuffer) -> T): T
}