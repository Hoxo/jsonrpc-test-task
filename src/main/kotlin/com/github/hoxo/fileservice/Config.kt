package com.github.hoxo.fileservice

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("app")
data class Config(
    var rootDir: String = "/tmp",
    var maxFileChunkSize: Int = 1024 * 1024 * 10,
) {

    @ConfigurationProperties("buffer-pool")
    data class BufferPool(
        var enabled: Boolean = false,
        var size: Int = 100,
        var bufferSize: Int = 1024
    )

    @ConfigurationProperties("simple-allocator")
    data class SimpleAllocator(
        var enabled: Boolean = false,
        var bufferSize: Int = 8192
    )
}