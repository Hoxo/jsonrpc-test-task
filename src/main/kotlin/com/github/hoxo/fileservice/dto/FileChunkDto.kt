package com.github.hoxo.fileservice.dto

data class FileChunkDto(
    val info: FileInfoDto,
    val offset: Long,
    val size: Int,
    val data: String,
    val hasRemainingData: Boolean,
)
