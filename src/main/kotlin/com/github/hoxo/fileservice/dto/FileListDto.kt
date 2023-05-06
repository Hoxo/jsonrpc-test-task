package com.github.hoxo.fileservice.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude
data class FileListDto(
    val result: List<FileInfoDto>
)