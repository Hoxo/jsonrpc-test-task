package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.client.FileServiceClient
import com.github.hoxo.fileservice.client.ListParams
import com.github.hoxo.fileservice.jsonrpc.JsonRpcResponse
import com.github.hoxo.fileservice.service.FileService
import io.micronaut.context.annotation.Primary
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito

private const val PARSE_ERROR = -32700
private const val INVALID_REQUEST = -32600
private const val METHOD_NOT_FOUND = -32601
private const val INVALID_PARAMS = -32602
private const val INTERNAL_ERROR = -32603

@MicronautTest
class JsonRpcApiTest {

    @Inject
    private lateinit var client: FileServiceClient

    @Primary
    @MockBean
    private fun fileService(): FileService {
        return Mockito.mock(FileService::class.java)
    }

    @Test
    fun `server should return parse error on non-json request`() {
        val response = client.rawRequest("hehe")
        assertJsonRpcError(response, "null")
        assertEquals(PARSE_ERROR, response.error!!.code)
    }

    @Disabled("lib implementation is not correct")
    @Test
    fun `server should return invalid request on incorrect request`() {
        val response = client.rawRequest("{}")
        assertJsonRpcError(response, "null")
        assertEquals(INVALID_REQUEST, response.error!!.code)
    }

    @Test
    fun `server should return method not found on random request`() {
        val response = client.rawRequest("{\"jsonrpc\":\"2.0\",\"method\":\"hehe\",\"id\":\"1\"}")
        assertJsonRpcError(response, "1")
        assertEquals(METHOD_NOT_FOUND, response.error!!.code)
    }

    @Test
    fun `server should return invalid params on incorrect params`() {
        val response = client.rawRequest("{\"jsonrpc\":\"2.0\",\"method\":\"list\",\"id\":\"1\",\"params\":{}}")
        assertJsonRpcError(response, "1")
        assertEquals(INVALID_PARAMS, response.error!!.code)
    }

    @Test
    fun `server should return internal error on internal error`(): Unit = runBlocking {
        Mockito.`when`(fileService().list("/")).thenThrow(RuntimeException("hehe"))
        val response = client.list(1, ListParams("/"))
        assertJsonRpcError(response, "1")
        assertEquals(INTERNAL_ERROR, response.error!!.code)
    }

}

fun assertJsonRpcResponse(response: JsonRpcResponse<*>, id: String, nullable: Boolean = false) {
    assertEquals(id, response.id)
    assertEquals("2.0", response.jsonrpc)
    assertNull(response.error)
    if (!nullable) {
        assertNotNull(response.result)
    }
}

fun assertJsonRpcError(response: JsonRpcResponse<*>, id: String?) {
    assertEquals(id, response.id)
    assertEquals("2.0", response.jsonrpc)
    assertNotNull(response.error)
    assertNull(response.result)
}