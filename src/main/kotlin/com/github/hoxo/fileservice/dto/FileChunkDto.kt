package com.github.hoxo.fileservice.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude
data class FileChunkDto(
    val info: FileInfoDto,
    val offset: Long,
    val size: Int,
    val data: String,
    val hasRemainingData: Boolean,
)
