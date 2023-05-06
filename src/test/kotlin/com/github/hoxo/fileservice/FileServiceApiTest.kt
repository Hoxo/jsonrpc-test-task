package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.client.FileServiceClient
import com.github.hoxo.fileservice.client.GetInfoParams
import com.github.hoxo.fileservice.client.ListParams
import com.github.hoxo.fileservice.dto.FileInfoDto
import com.github.hoxo.fileservice.jsonrpc.JsonRpcErrors
import com.github.hoxo.fileservice.jsonrpc.JsonRpcResponse
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.writeText

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS) //need for dynamic properties
class FileServiceApiTest: TestPropertyProvider {
    @Inject
    private lateinit var client: FileServiceClient

    private lateinit var rootDir: Path

    override fun getProperties(): MutableMap<String, String> {
        rootDir = Path.of("/tmp", "test-${UUID.randomUUID()}")
        return mutableMapOf("app.root-dir" to rootDir.absolutePathString())
    }

    @BeforeEach
    fun setup() {
        Files.createDirectory(rootDir)
    }

    @AfterEach
    fun cleanup() {
        rootDir.toFile().deleteRecursively()
    }

    @Test
    fun `list should work`() {
        rootDir.resolve("file1").createFile()
        rootDir.resolve("file2").createFile().writeText("hello")
        rootDir.resolve("dir1").createDirectory()

        val response = client.list(1, ListParams("/"))

        assertJsonRpcResponse(response, "1")
        val files = response.result!!.result
        assertEquals(3, files.size)
        assertTrue(files.contains(FileInfoDto("file1", 0, "/file1", false)))
        assertTrue(files.contains(FileInfoDto("file2", 5, "/file2", false)))
        assertTrue(files.contains(FileInfoDto("dir1", LINUX_DIR_SIZE, "/dir1", true)))
    }

    @Test
    fun `list should return error on unknown dir`() {
        val response = client.list(1, ListParams("/unknown"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.NOT_FOUND.copy(data = JsonRpcResponse.Error.Data(
            message = "NoSuchFileException: /unknown"
        )), response.error)
    }

    @Test
    fun `list should return error on file`() {
        rootDir.resolve("file1").createFile()

        val response = client.list(1, ListParams("/file1"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.BAD_REQUEST.copy(data = JsonRpcResponse.Error.Data(
            message = "NotDirectoryException: /file1"
        )), response.error)
    }

    @Test
    fun `getInfo should work`() {
        val response = client.getInfo(1, GetInfoParams(""))
        assertJsonRpcResponse(response, "1")
        assertEquals(FileInfoDto("", LINUX_DIR_SIZE, "/", true), response.result!!)
    }

    private fun assertJsonRpcResponse(response: JsonRpcResponse<*>, id: String) {
        assertEquals(id, response.id)
        assertEquals("2.0", response.jsonrpc)
        assertNull(response.error)
        assertNotNull(response.result)
    }

    private fun assertJsonRpcError(response: JsonRpcResponse<*>, id: String) {
        assertEquals(id, response.id)
        assertEquals("2.0", response.jsonrpc)
        assertNotNull(response.error)
        assertNull(response.result)
    }

}
