package com.github.hoxo.fileservice.dto

data class FileChunkDto(
    val info: FileInfoDto,
    val offset: Long,
    val size: Long,
    val data: String,
    val hasRemainingData: Boolean,
)
