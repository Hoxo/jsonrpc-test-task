package com.github.hoxo.fileservice.model


/**
 * File chunk.
 */
class FileChunk(
    /**
     * File info.
     */
    val info: FileInfo,
    /**
     * Offset in bytes from the beginning of the file.
     */
    val offset: Long,
    /**
     * Size of the chunk in bytes.
     */
    val size: Int,
    /**
     * Data of the chunk.
     */
    val data: ByteArray,
    /**
     * Is there more data to read from file.
     */
    val hasRemainingData: Boolean,
)
