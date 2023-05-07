package com.github.hoxo.fileservice

import io.micronaut.context.annotation.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app")
data class Config(
    /**
     * Directory to manage files in. All paths will be relative to this directory.
     * User will not be able to access files outside this directory.
     */
    var rootDir: String = "/tmp",
    /**
     * Maximum size of file chunk to read from disk. This is used to limit memory usage.
     */
    var maxFileChunkSize: Int = 1024 * 1024 * 10,
) {
    @ConfigurationProperties("simple-allocator")
    data class SimpleAllocator(
        /**
         * Enable simple allocator buffer allocator, which will allocate buffer for each request
         */
        var enabled: Boolean = false,
        /**
         * Size of buffer to allocate for each request
         */
        var bufferSize: Int = 8192
    )

    @ConfigurationProperties("file-service")
    data class FileService(
        /**
         * Number of threads to use for file operations
         */
        var poolSize: Int = 10,
    )

    @ConfigurationProperties("file-locking")
    data class FileLocking(
        /**
         * Enable file locking
         */
        var enabled: Boolean = true,
        /**
         * Timeout for read lock
         */
        var readTimeout: Duration = Duration.ofMillis(1000),
        /**
         * Timeout for write lock
         */
        var writeTimeout: Duration = Duration.ofMillis(1000),
    )
}