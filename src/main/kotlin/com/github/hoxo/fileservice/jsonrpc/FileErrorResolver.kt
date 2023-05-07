package com.github.hoxo.fileservice.jsonrpc

import com.fasterxml.jackson.databind.JsonNode
import com.github.hoxo.fileservice.Config
import com.googlecode.jsonrpc4j.ErrorResolver
import io.micronaut.http.exceptions.ContentLengthExceededException
import jakarta.inject.Singleton
import java.io.IOException
import java.lang.reflect.Method
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException

@Singleton
class FileErrorResolver(
    private val config: Config
): ErrorResolver {

    override fun resolveError(
        t: Throwable,
        method: Method,
        arguments: List<JsonNode>
    ): ErrorResolver.JsonError {
        return resolveError(t).toJsonError()
    }

    fun resolveError(t: Throwable): JsonRpcResponse.Error {
        return when (t) {
            is IllegalArgumentException -> JsonRpcErrors.BAD_REQUEST.copy(data = toData(t))
            is NoSuchFileException -> JsonRpcErrors.NOT_FOUND.copy(data = toData(t))
            is FileAlreadyExistsException -> JsonRpcErrors.CONFLICT.copy(data = toData(t))
            is IOException -> JsonRpcErrors.BAD_REQUEST.copy(data = toData(t))
            is ContentLengthExceededException -> JsonRpcErrors.REQUEST_TOO_LARGE.copy(data = toData(t))
            else -> JsonRpcErrors.INTERNAL_ERROR.copy(data = toData(t))
        }
    }

    private fun toData(t: Throwable) = JsonRpcResponse.Error.Data(
        message = "${t.javaClass.simpleName}: ${hideRootPath(t.message)}"
    )

    private fun hideRootPath(message: String?) = message
        ?.replace("${config.rootDir}/", "/")
        ?.replace(config.rootDir, "")
}

private fun JsonRpcResponse.Error.toJsonError() = ErrorResolver.JsonError(code, message, data)
