package com.github.hoxo.fileservice

import com.googlecode.jsonrpc4j.JsonRpcServer
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import java.io.ByteArrayOutputStream

@Controller("/file-service.json", produces = ["application/json"], consumes = ["application/json"])
class FileController(
    private val jsonRpcServer: JsonRpcServer
) {
    @Post
    fun jsonRpc(@Body request: String): String {
        val outputStream = ByteArrayOutputStream()
        jsonRpcServer.handleRequest(request.byteInputStream(), outputStream)
        return outputStream.toString()
    }
}