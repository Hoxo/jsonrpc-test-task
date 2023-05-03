package com.github.hoxo.fileservice.buffer

import jakarta.inject.Singleton
import java.nio.ByteBuffer

@Singleton
class SimpleBufferAllocator: BufferAllocator {
    override suspend fun allocate(size: Int): ByteBuffer {
        return ByteBuffer.allocate(size)
    }

    override suspend fun <T> borrow(size: Int, f: suspend (ByteBuffer) -> T): T {
        val buffer = allocate(size)
        return f(buffer)
    }
}