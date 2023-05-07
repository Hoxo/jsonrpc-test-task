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

    @JsonRpcMethod("getInfo")
    fun getInfo(@JsonRpcParam("path") path: String): FileInfoDto {
        val result = runBlocking(Dispatchers.Default) {
            fileService.getInfo(path)
        }
        return result.toDto()
    }

    @JsonRpcMethod("list")
    fun listFiles(@JsonRpcParam("path") path: String): FileListDto {
        return runBlocking(Dispatchers.Default) {
            val result = fileService.list(path)
                .map { it.toDto() }
                .toList()
            FileListDto(result)
        }
    }

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

    @JsonRpcMethod("createEmptyFile")
    fun createEmptyFile(@JsonRpcParam("path") path: String): FileInfoDto {
        val result = runBlocking(Dispatchers.Default) {
            fileService.createEmptyFile(path)
        }
        return result.toDto()
    }

    @JsonRpcMethod("createEmptyDir")
    fun createEmptyDir(@JsonRpcParam("path") path: String): FileInfoDto {
        val result = runBlocking(Dispatchers.Default) {
            fileService.createEmptyDir(path)
        }
        return result.toDto()
    }

    @JsonRpcMethod("delete")
    fun delete(@JsonRpcParam("path") path: String) {
        runBlocking(Dispatchers.Default) {
            fileService.delete(path)
        }
    }

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