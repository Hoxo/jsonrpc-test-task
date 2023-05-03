package com.github.hoxo.fileservice.buffer

import java.nio.ByteBuffer

interface BufferAllocator {
    suspend fun <T> borrow(f: suspend (ByteBuffer) -> T): T
}