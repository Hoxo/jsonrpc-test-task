package com.github.hoxo.fileservice.dto

import com.github.hoxo.fileservice.model.FileInfo

data class FileInfoDto(
    val name: String,
    val size: Long,
    val path: String,
    val isDirectory: Boolean,
)

fun FileInfo.toDto(): FileInfoDto {
    return FileInfoDto(
        name = this.name,
        size = this.size,
        path = this.path,
        isDirectory = this.isDirectory,
    )
}
