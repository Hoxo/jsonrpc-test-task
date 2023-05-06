package com.github.hoxo.fileservice.client

import com.fasterxml.jackson.annotation.JsonInclude
import com.github.hoxo.fileservice.dto.FileChunkDto
import com.github.hoxo.fileservice.dto.FileInfoDto
import com.github.hoxo.fileservice.dto.FileListDto
import com.github.hoxo.fileservice.jsonrpc.JsonRpcResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client

@Client("/file-service.json")
interface FileServiceClient {

    @Post(consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON])
    fun rawRequest(@Body json: String): JsonRpcResponse<*>

    @Post(consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON])
    fun list(id: Int, params: ListParams, method: String = "list",
             jsonrpc: String = "2.0"): JsonRpcResponse<FileListDto>

    @Post(consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON])
    fun getInfo(id: Int, params: GetInfoParams, method: String = "getInfo",
                jsonrpc: String = "2.0"): JsonRpcResponse<FileInfoDto>

    @Post(consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON])
    fun readFile(id: Int, params: ReadFileParams, method: String = "readFile",
                 jsonrpc: String = "2.0"): JsonRpcResponse<FileChunkDto>

    @Post(consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON])
    fun move(id: Int, params: MoveParams, method: String = "move",
             jsonrpc: String = "2.0"): JsonRpcResponse<String>

    @Post(consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON])
    fun copy(id: Int, params: CopyParams, method: String = "copy",
             jsonrpc: String = "2.0"): JsonRpcResponse<String>

    @Post(consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON])
    fun delete(id: Int, params: DeleteParams, method: String = "delete",
               jsonrpc: String = "2.0"): JsonRpcResponse<Unit?>

    @Post(consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON])
    fun createDir(id: Int, params: CreateDirParams, method: String = "createEmptyDir",
                  jsonrpc: String = "2.0"): JsonRpcResponse<String>

    @Post(consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON])
    fun createFile(id: Int, params: CreateFileParams, method: String = "createEmptyFile",
                   jsonrpc: String = "2.0"): JsonRpcResponse<String>

    @Post(consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON])
    fun appendFile(id: Int, params: AppendFileParams, method: String = "appendFile",
                   jsonrpc: String = "2.0"): JsonRpcResponse<FileInfoDto>

}

@JsonInclude
data class GetInfoParams(val path: String)

@JsonInclude
data class ListParams(val path: String)

@JsonInclude
data class ReadFileParams(val path: String, val offset: Long, val size: Int)

@JsonInclude
data class MoveParams(val path: String, val newPath: String)

@JsonInclude
data class CopyParams(val path: String, val newPath: String)

@JsonInclude
data class DeleteParams(val path: String)

@JsonInclude
data class CreateDirParams(val path: String)

@JsonInclude
data class CreateFileParams(val path: String)

@JsonInclude
data class AppendFileParams(val path: String, val data: String)