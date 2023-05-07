package com.github.hoxo.fileservice.jsonrpc

import com.github.hoxo.fileservice.dto.FileChunkDto
import com.github.hoxo.fileservice.dto.FileInfoDto
import com.github.hoxo.fileservice.dto.FileListDto
import com.github.hoxo.fileservice.dto.toDto
import com.github.hoxo.fileservice.service.FileService
import com.googlecode.jsonrpc4j.*
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.util.*

@JsonRpcService("FileService")
@Singleton
class JsonRpcFileService(
    private val fileService: FileService
) {
    private val base64Decoder = Base64.getDecoder()
    private val base64Encoder = Base64.getEncoder()

    /**
     * Get file info.
     * @param path path to file
     * @return FileInfo object with file info
     * @see [FileService.getInfo]
     */
    @JsonRpcMethod("getInfo")
    fun getInfo(@JsonRpcParam("path") path: String): FileInfoDto {
        val result = runBlocking(Dispatchers.Default) {
            fileService.getInfo(path)
        }
        return result.toDto()
    }

    /**
     * List files in given directory.
     * @param path path to directory
     * @return FileList object with list of files
     * @see [FileService.list]
     */
    @JsonRpcMethod("list")
    fun listFiles(@JsonRpcParam("path") path: String): FileListDto {
        return runBlocking(Dispatchers.Default) {
            val result = fileService.list(path)
                .map { it.toDto() }
                .toList()
            FileListDto(result)
        }
    }

    /**
     * Read file content with given offset and amount.
     * @param path path to file
     * @param offset offset in bytes
     * @param toRead number of bytes to read
     * @return FileChunk object with file data
     * @see [FileService.readFile]
     */
    @JsonRpcMethod("readFile")
    fun readFile(
        @JsonRpcParam("path") path: String,
        @JsonRpcParam("offset") offset: Long,
        @JsonRpcParam("toRead") toRead: Int
    ): FileChunkDto {
        val content = runBlocking(Dispatchers.Default) {
            fileService.readFile(path, offset, toRead)
        }
        return FileChunkDto(
            info = content.info.toDto(),
            offset = content.offset,
            size = content.size,
            data = base64Encoder.encodeToString(content.data),
            hasRemainingData = content.hasRemainingData,
        )
    }

    /**
     * Create empty file.
     * @param path path to file to create
     * @return FileInfo object with file info
     * @see [FileService.createEmptyFile]
     */
    @JsonRpcMethod("createEmptyFile")
    fun createEmptyFile(@JsonRpcParam("path") path: String): FileInfoDto {
        val result = runBlocking(Dispatchers.Default) {
            fileService.createEmptyFile(path)
        }
        return result.toDto()
    }

    /**
     * Create empty directory.
     * @param path path to directory to create
     * @return FileInfo object with directory info
     * @see [FileService.createEmptyDir]
     */
    @JsonRpcMethod("createEmptyDir")
    fun createEmptyDir(@JsonRpcParam("path") path: String): FileInfoDto {
        val result = runBlocking(Dispatchers.Default) {
            fileService.createEmptyDir(path)
        }
        return result.toDto()
    }

    /**
     * Delete file or directory. Directory must be empty.
     * @param path path to file or directory
     * @see [FileService.delete]
     */
    @JsonRpcMethod("delete")
    fun delete(@JsonRpcParam("path") path: String) {
        runBlocking(Dispatchers.Default) {
            fileService.delete(path)
        }
    }

    /**
     * Move file or directory.
     * @param path path to file or directory
     * @param newPath path to new file or directory
     * @return path to new file or directory
     * @see [FileService.move]
     */
    @JsonRpcMethod("move")
    fun move(
        @JsonRpcParam("path") path: String,
        @JsonRpcParam("newPath") newPath: String
    ): String {
        val result = runBlocking(Dispatchers.Default) {
            fileService.move(path, newPath)
        }
        return result
    }

    /**
     * Copy file or directory.
     * @param path path to file or directory
     * @param newPath path to new file or directory
     * @return path to new file or directory
     * @see [FileService.copy]
     */
    @JsonRpcMethod("copy")
    fun copy(
        @JsonRpcParam("path") path: String,
        @JsonRpcParam("newPath") newPath: String
    ): String {
        val result = runBlocking(Dispatchers.Default) {
            fileService.copy(path, newPath)
        }
        return result
    }

    /**
     * Append data to the end of the file.
     * @param path path to file
     * @param data base64 encoded data
     * @return FileInfoDto
     * @see [FileService.append]
     */
    @JsonRpcMethod("append")
    fun append(
        @JsonRpcParam("path") path: String,
        @JsonRpcParam("data") data: String
    ): FileInfoDto {
        val result = runBlocking(Dispatchers.Default) {
            fileService.append(path, base64Decoder.decode(data))
        }
        return result.toDto()
    }

    /**
     * Write data to file with offset.
     * @param path path to file
     * @param offset offset in file
     * @param data base64 encoded data
     * @return FileInfoDto
     * @see [FileService.write]
     */
    @JsonRpcMethod("write")
    fun write(
        @JsonRpcParam("path") path: String,
        @JsonRpcParam("offset") offset: Long,
        @JsonRpcParam("data") data: String
    ): FileInfoDto {
        val result = runBlocking(Dispatchers.Default) {
            fileService.write(path, offset, base64Decoder.decode(data))
        }
        return result.toDto()
    }

}