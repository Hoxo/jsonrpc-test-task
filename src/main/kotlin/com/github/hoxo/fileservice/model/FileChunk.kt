package com.github.hoxo.fileservice.model

data class FileChunk(
    val info: FileInfo,
    val offset: Long,
    val size: Long,
    val data: ByteArray,
    val hasRemainingData: Boolean,
)
