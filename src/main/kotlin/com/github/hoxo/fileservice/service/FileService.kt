package com.github.hoxo.fileservice.service

import com.github.hoxo.fileservice.model.FileChunk
import com.github.hoxo.fileservice.model.FileInfo
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/**
 * Service to manipulate files on some abstract file system.
 */
interface FileService {
    /**
     * Fetch file info (e.g. name, size, path, type).
     * @param path path to file or directory
     * @return FileInfo object with file info
     * @throws java.io.IOException if file not exists or info can't be fetched
     */
    @Throws(IOException::class)
    suspend fun getInfo(path: String): FileInfo

    /**
     * List all direct children of directory.
     * @param path path to directory
     * @return async Flow of FileInfo objects with file info
     * @throws java.io.IOException if path is not a directory, not exists or can't be listed
     */
    @Throws(IOException::class)
    suspend fun list(path: String): Flow<FileInfo>

    /**
     * Read chunk from file with given offset and amount.
     * @param path path to file
     * @param offset offset in bytes
     * @param toRead number of bytes to read
     * @return FileChunk object with file data
     * @throws IllegalArgumentException in case if:
     * * offset is negative;
     * * toRead is negative or equals to zero;
     * @throws java.io.IOException if file not exists, is not a file or can't be read
     */
    @Throws(IOException::class)
    suspend fun readFile(path: String, offset: Long, toRead: Int): FileChunk

    /**
     * Create empty file with given path.
     * @param path path to file to create
     * @return FileInfo object with file info
     * @throws java.io.IOException if file already exists or can't be created
     */
    @Throws(IOException::class)
    suspend fun createEmptyFile(path: String): FileInfo

    /**
     * Create empty directory with given path.
     * @param path path to directory to create
     * @return FileInfo object with file info
     * @throws java.io.IOException if directory already exists or can't be created
     */
    @Throws(IOException::class)
    suspend fun createEmptyDir(path: String): FileInfo

    /**
     * Delete file or directory with given path. If directory is not empty, it won't be deleted.
     * @param path path to file to delete
     * @throws IllegalArgumentException if path is root
     * @throws java.io.IOException if file or directory not exists or can't be deleted
     */
    @Throws(IOException::class)
    suspend fun delete(path: String)

    /**
     * Move file or directory with given path to new path. If directory is not empty, it's content will be moved too.
     * @param path path to file or directory to move
     * @param newPath new path to file or directory
     * @return new path to file or directory
     * @throws IllegalArgumentException if path or newPath are root
     * @throws java.io.IOException other IO errors
     */
    @Throws(IOException::class)
    suspend fun move(path: String, newPath: String): String

    /**
     * Copy file or directory with given path to new path. If directory is not empty, it's content will be copied too.
     * @param path path to file or directory to copy
     * @param newPath new path to file or directory
     * @return new path to file or directory
     * @throws IllegalArgumentException if path or newPath are root
     * @throws java.io.IOException if file or directory not exists or can't be copied, or new path is not writable
     */
    @Throws(IOException::class)
    suspend fun copy(path: String, newPath: String): String

    /**
     * Append data to the end of the file.
     * @param path path to file
     * @param data data to append
     * @throws IllegalArgumentException if data is empty
     * @throws java.io.IOException if path is not a file, if file not exists or can't be appended
     */
    @Throws(IOException::class)
    suspend fun append(path: String, data: ByteArray): FileInfo
}