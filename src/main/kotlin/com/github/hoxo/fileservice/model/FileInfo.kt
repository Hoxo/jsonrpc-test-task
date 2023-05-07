package com.github.hoxo.fileservice.model

/**
 * File info.
 */
data class FileInfo(
    /**
     * File name.
     */
    val name: String,
    /**
     * Absolute path to file.
     */
    val path: String,
    /**
     * File size in bytes.
     */
    val size: Long,
    /**
     * Is file a directory.
     */
    val isDirectory: Boolean,
)