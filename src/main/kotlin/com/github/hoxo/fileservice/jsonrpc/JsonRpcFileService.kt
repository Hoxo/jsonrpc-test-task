package com.github.hoxo.fileservice.jsonrpc

import com.github.hoxo.fileservice.dto.FileChunkDto
import com.github.hoxo.fileservice.dto.FileInfoDto
import com.github.hoxo.fileservice.dto.FileListDto
import com.github.hoxo.fileservice.dto.toDto
import com.github.hoxo.fileservice.service.FileService
import com.googlecode.jsonrpc4j.*
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.util.*

//todo use own thread pool to run blocking code
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
        return result.getOrThrow().toDto()
    }

    @JsonRpcMethod("list")
    fun listFiles(@JsonRpcParam("path") path: String): FileListDto {
        return runBlocking(Dispatchers.Default) {
            FileListDto(fileService.list(path).getOrThrow().toList().map { it.toDto() })
        }
    }

    @JsonRpcMethod("readFile")
    fun readFile(
        @JsonRpcParam("path") path: String,
        @JsonRpcParam("offset") offset: Long,
        @JsonRpcParam("toRead") toRead: Int
    ): FileChunkDto {
        val content = runBlocking(Dispatchers.Default) {
            fileService.readFile(path, offset, toRead).getOrThrow()
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
        return result.getOrThrow().toDto()
    }

    @JsonRpcMethod("createEmptyDir")
    fun createEmptyDir(@JsonRpcParam("path") path: String): FileInfoDto {
        val result = runBlocking(Dispatchers.Default) {
            fileService.createEmptyDir(path)
        }
        return result.getOrThrow().toDto()
    }

    @JsonRpcMethod("delete")
    fun delete(@JsonRpcParam("path") path: String) {
        runBlocking(Dispatchers.Default) {
            fileService.delete(path).getOrThrow()
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
        return result.getOrThrow()
    }

    @JsonRpcMethod("copy")
    fun copy(
        @JsonRpcParam("path") path: String,
        @JsonRpcParam("newPath") newPath: String
    ): String {
        val result = runBlocking(Dispatchers.Default) {
            fileService.copy(path, newPath)
        }
        return result.getOrThrow()
    }


    @JsonRpcMethod("append")
    fun append(
        @JsonRpcParam("path") path: String,
        @JsonRpcParam("data") data: String
    ): FileInfoDto {
        val result = runBlocking(Dispatchers.Default) {
            fileService.append(path, base64Decoder.decode(data))
        }
        return result.getOrThrow().toDto()
    }

}