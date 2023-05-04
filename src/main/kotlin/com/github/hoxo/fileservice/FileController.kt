package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.dto.JsonRpcResponse
import com.googlecode.jsonrpc4j.ErrorResolver.JsonError
import com.googlecode.jsonrpc4j.JsonRpcServer
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.exceptions.ContentLengthExceededException
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.server.exceptions.HttpServerException
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

private val LOG = LoggerFactory.getLogger(FileController::class.java)

@Controller("/file-service.json", produces = ["application/json"], consumes = ["application/json"])
class FileController(
    private val jsonRpcServer: JsonRpcServer
) {
    @OptIn(ExperimentalTime::class)
    @Post
    fun jsonRpc(@Body request: String): String {
        val outputStream = ByteArrayOutputStream()
        return measureTimedValue {
            jsonRpcServer.handleRequest(request.byteInputStream(), outputStream)
            outputStream.toString()
        }.also {
            LOG.debug("file-service.json request: {}", request)
            LOG.info("file-service.json request: took {}ms", it.duration.toDouble(DurationUnit.MILLISECONDS))
        }.value
    }

    @Error(exception = Exception::class)
    fun error(e: Exception): HttpResponse<JsonRpcResponse> {
        LOG.error("file-service.json error", e)
        val jsonRpcCode = when (e) {
            is HttpServerException -> JsonError.INTERNAL_ERROR.code
            is HttpClientException -> JsonError.INVALID_REQUEST.code
            else -> JsonError.INTERNAL_ERROR.code
        }
        //todo expand later
        val httpStatus = when (e) {
            is HttpStatusException -> e.status
            is ContentLengthExceededException -> HttpStatus.REQUEST_ENTITY_TOO_LARGE
            is HttpClientException -> HttpStatus.BAD_REQUEST
            is HttpServerException -> HttpStatus.INTERNAL_SERVER_ERROR
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        return HttpResponse
            .status<JsonRpcResponse?>(httpStatus)
            .body(JsonRpcResponse(error = JsonError(jsonRpcCode, e.message, null)))
    }
}