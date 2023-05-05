package com.github.hoxo.fileservice.model

data class FileChunk(
    val info: FileInfo,
    val offset: Long,
    val size: Int,
    val data: ByteArray,
    val hasRemainingData: Boolean,
)
