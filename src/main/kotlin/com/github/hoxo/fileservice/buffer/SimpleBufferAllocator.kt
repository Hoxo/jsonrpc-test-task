package com.github.hoxo.fileservice.buffer

import com.github.hoxo.fileservice.Config
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.nio.ByteBuffer

@Requires(property = "app.simple-allocator.enabled", value = "true")
@Singleton
class SimpleBufferAllocator(
    private val config: Config.SimpleAllocator
): BufferAllocator {
    override suspend fun <T> borrow(f: suspend (ByteBuffer) -> T): T {
        val buffer = ByteBuffer.allocate(config.bufferSize)
        return f(buffer)
    }
}