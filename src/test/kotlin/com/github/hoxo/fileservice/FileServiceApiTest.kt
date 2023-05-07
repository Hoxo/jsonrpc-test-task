package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.client.*
import com.github.hoxo.fileservice.dto.FileInfoDto
import com.github.hoxo.fileservice.jsonrpc.JsonRpcErrors
import com.github.hoxo.fileservice.jsonrpc.JsonRpcResponse
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
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
import kotlin.io.path.*
import kotlin.random.Random

private val RANDOM = Random(42)
private const val MAX_CHUNK_SIZE = 10000
private const val BUFFER_SIZE = 999
private const val MAX_REQUEST_SIZE = 1000

@Property(name = "micronaut.server.max-request-size", value = MAX_REQUEST_SIZE.toString())
@Property(name = "app.max-file-chunk-size", value = MAX_CHUNK_SIZE.toString())
@Property(name = "app.simple-allocator.buffer-size", value = BUFFER_SIZE.toString())
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS) //need for dynamic properties
class FileServiceApiTest: TestPropertyProvider {
    private val decoder = Base64.getDecoder()
    private val encoder = Base64.getEncoder()

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
        var response = client.getInfo(1, GetInfoParams(""))
        assertJsonRpcResponse(response, "1")
        assertEquals(FileInfoDto("", LINUX_DIR_SIZE, "/", true), response.result!!)

        val testDir = rootDir.resolve("dir1").createDirectory()
        testDir.resolve("file1").createFile().writeText("hello")
        response = client.getInfo(2, GetInfoParams("dir1/file1"))
        assertJsonRpcResponse(response, "2")
        assertEquals(FileInfoDto("file1", 5, "/dir1/file1", false), response.result!!)
    }

    @Test
    fun `getInfo should return error on unknown file`() {
        val response = client.getInfo(1, GetInfoParams("unknown"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.NOT_FOUND.copy(data = JsonRpcResponse.Error.Data(
            message = "NoSuchFileException: /unknown"
        )), response.error)
    }

    @Test
    fun `readFile should work`() {
        val data = RANDOM.nextBytes(MAX_CHUNK_SIZE + 1)
        rootDir.resolve("file1").createFile().writeBytes(data)

        val response = client.readFile(1, ReadFileParams("file1", 0, 100))
        assertJsonRpcResponse(response, "1")
        val result = response.result!!
        assertArrayEquals(data.sliceArray(0 until 100), decoder.decode(result.data))
        assertEquals(100, result.size)
        assertEquals(0, result.offset)
        assertTrue(result.hasRemainingData)
        assertEquals(FileInfoDto("file1", data.size.toLong(), "/file1", false), result.info)
    }

    @Test
    fun `readFile should work with offset`() {
        val fileSize = MAX_CHUNK_SIZE + 1
        val data = RANDOM.nextBytes(fileSize)
        rootDir.resolve("file1").createFile().writeBytes(data)

        val offset = 1234L
        val response = client.readFile(1, ReadFileParams("file1", offset, MAX_CHUNK_SIZE))
        assertJsonRpcResponse(response, "1")
        val result = response.result!!
        assertArrayEquals(data.sliceArray(offset.toInt() until fileSize), decoder.decode(result.data))
        assertEquals(fileSize - offset.toInt(), result.size)
        assertEquals(offset, result.offset)
        assertFalse(result.hasRemainingData)
        assertEquals(FileInfoDto("file1", data.size.toLong(), "/file1", false), result.info)
    }

    @Test
    fun `readFile should read zero with offset out of range`() {
        val fileSize = MAX_CHUNK_SIZE + 1
        val data = RANDOM.nextBytes(fileSize)
        rootDir.resolve("file1").createFile().writeBytes(data)

        val response = client.readFile(1, ReadFileParams("file1", fileSize + 1L, 100))

        assertJsonRpcResponse(response, "1")
        val result = response.result!!
        assertArrayEquals(ByteArray(0), decoder.decode(result.data))
        assertEquals(0, result.size)
        assertEquals(fileSize + 1L, result.offset)
        assertFalse(result.hasRemainingData)
        assertEquals(FileInfoDto("file1", fileSize.toLong(), "/file1", false), result.info)
    }

    @Test
    fun `readFile should return error on unknown file`() {
        val response = client.readFile(1, ReadFileParams("unknown", 0, 100))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.NOT_FOUND.copy(data = JsonRpcResponse.Error.Data(
            message = "NoSuchFileException: /unknown"
        )), response.error)
    }

    @Test
    fun `readFile should return error on invalid params`() {
        var response = client.readFile(1, ReadFileParams("unknown", -1, 100))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.BAD_REQUEST.copy(data = JsonRpcResponse.Error.Data(
            message = "IllegalArgumentException: offset must be non-negative"
        )), response.error)

        response = client.readFile(1, ReadFileParams("unknown", 0, -1))
        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.BAD_REQUEST.copy(data = JsonRpcResponse.Error.Data(
            message = "IllegalArgumentException: toRead must be positive"
        )), response.error)

        response = client.readFile(1, ReadFileParams("unknown", 0, MAX_CHUNK_SIZE + 1))
        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.BAD_REQUEST.copy(data = JsonRpcResponse.Error.Data(
            message = "IllegalArgumentException: toRead must be less than $MAX_CHUNK_SIZE bytes"
        )), response.error)
    }

    @Test
    fun `move should work with files`() {
        val testDir = rootDir.resolve("dir1").createDirectory()
        testDir.resolve("file1").createFile()

        val response = client.move(1, MoveParams("dir1/file1", "dir1/file2"))

        assertJsonRpcResponse(response, "1")
        val result = response.result!!
        assertEquals("/dir1/file2", result)
        assertTrue(testDir.resolve("file1").notExists())
        assertTrue(testDir.resolve("file2").exists())
    }

    @Test
    fun `move should work with dir`() {
        val testDir = rootDir.resolve("dir1").createDirectory()
        testDir.resolve("file1").createFile()

        val response = client.move(1, MoveParams("dir1", "dir2"))

        assertJsonRpcResponse(response, "1")
        val result = response.result!!
        assertEquals("/dir2", result)
        assertTrue(rootDir.resolve("dir1").notExists())
        assertTrue(rootDir.resolve("dir2").exists())
        assertTrue(rootDir.resolve("dir2/file1").exists())
    }

    @Test
    fun `move should return error on unknown file`() {
        val response = client.move(1, MoveParams("unknown", "dir1/file2"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.NOT_FOUND.copy(data = JsonRpcResponse.Error.Data(
            message = "NoSuchFileException: /unknown"
        )), response.error)
    }

    @Test
    fun `move should return error on unknown target`() {
        val testDir = rootDir.resolve("dir1").createDirectory()
        testDir.resolve("file1").createFile()

        val response = client.move(1, MoveParams("dir1/file1", "unknown/file2"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.NOT_FOUND.copy(data = JsonRpcResponse.Error.Data(
            message = "NoSuchFileException: /dir1/file1 -> /unknown/file2"
        )), response.error)
    }

    @Test
    fun `copy should work with files`() {
        val testDir = rootDir.resolve("dir1").createDirectory()
        testDir.resolve("file1").createFile().writeText("hello")

        val response = client.copy(1, CopyParams("dir1/file1", "dir1/file2"))

        assertJsonRpcResponse(response, "1")
        val result = response.result!!
        assertEquals("/dir1/file2", result)
        assertTrue(testDir.resolve("file1").exists())
        assertTrue(testDir.resolve("file2").exists())
        assertEquals("hello", testDir.resolve("file2").readText())
    }

    @Test
    fun `copy should work with dir`() {
        val testDir = rootDir.resolve("dir1").createDirectory()
        testDir.resolve("file1").createFile().writeText("hello")

        val response = client.copy(1, CopyParams("dir1", "dir2"))

        assertJsonRpcResponse(response, "1")
        val result = response.result!!
        assertEquals("/dir2", result)
        assertTrue(rootDir.resolve("dir1").exists())
        assertTrue(rootDir.resolve("dir2").exists())
        assertTrue(rootDir.resolve("dir2/file1").exists())
        assertEquals("hello", rootDir.resolve("dir2/file1").readText())
    }

    @Test
    fun `copy should return error on unknown file`() {
        val response = client.copy(1, CopyParams("unknown", "dir1/file2"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.NOT_FOUND.copy(data = JsonRpcResponse.Error.Data(
            message = "NoSuchFileException: /unknown"
        )), response.error)
    }

    @Test
    fun `copy should return error on unknown target`() {
        val testDir = rootDir.resolve("dir1").createDirectory()
        testDir.resolve("file1").createFile()

        val response = client.copy(1, CopyParams("dir1/file1", "unknown/file2"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.NOT_FOUND.copy(data = JsonRpcResponse.Error.Data(
            message = "NoSuchFileException: /unknown/file2"
        )), response.error)
    }

    @Test
    fun `delete should work with files`() {
        val testDir = rootDir.resolve("dir1").createDirectory()
        testDir.resolve("file1").createFile()

        val response = client.delete(1, DeleteParams("dir1/file1"))

        assertJsonRpcResponse(response, "1", nullable = true)
        assertTrue(testDir.resolve("file1").notExists())
    }

    @Test
    fun `delete should work with dir`() {
        val testDir = rootDir.resolve("dir1").createDirectory()
        testDir.resolve("file1").createFile()

        val response = client.delete(1, DeleteParams("dir1"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.BAD_REQUEST.copy(data = JsonRpcResponse.Error.Data(
            message = "DirectoryNotEmptyException: /dir1"
        )), response.error)
    }

    @Test
    fun `delete should return error on unknown file`() {
        val response = client.delete(1, DeleteParams("unknown"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.NOT_FOUND.copy(data = JsonRpcResponse.Error.Data(
            message = "NoSuchFileException: /unknown"
        )), response.error)
    }

    @Test
    fun `createDirectory should work`() {
        val response = client.createDir(1, CreateDirParams("dir1"))

        assertJsonRpcResponse(response, "1")
        val result = response.result!!
        assertEquals(FileInfoDto("dir1", LINUX_DIR_SIZE, "/dir1", true), result)
        assertTrue(rootDir.resolve("dir1").exists())
    }

    @Test
    fun `createDirectory should return error on existing file`() {
        rootDir.resolve("dir1").createDirectory()

        val response = client.createDir(1, CreateDirParams("dir1"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.CONFLICT.copy(data = JsonRpcResponse.Error.Data(
            message = "FileAlreadyExistsException: /dir1"
        )), response.error)
    }

    @Test
    fun `createDirectory should return error on unknown target`() {
        val response = client.createDir(1, CreateDirParams("unknown/dir1"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.NOT_FOUND.copy(data = JsonRpcResponse.Error.Data(
            message = "NoSuchFileException: /unknown/dir1"
        )), response.error)
    }

    @Test
    fun `createFile should work`() {
        val response = client.createFile(1, CreateFileParams("file1"))

        assertJsonRpcResponse(response, "1")
        val result = response.result!!
        assertEquals(FileInfoDto("file1", 0, "/file1", false), result)
        assertTrue(rootDir.resolve("file1").exists())
    }

    @Test
    fun `createFile should return error on existing file`() {
        rootDir.resolve("file1").createFile()

        val response = client.createFile(1, CreateFileParams("file1"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.CONFLICT.copy(data = JsonRpcResponse.Error.Data(
            message = "FileAlreadyExistsException: /file1"
        )), response.error)
    }

    @Test
    fun `createFile should return error on unknown target`() {
        val response = client.createFile(1, CreateFileParams("unknown/file1"))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.NOT_FOUND.copy(data = JsonRpcResponse.Error.Data(
            message = "NoSuchFileException: /unknown/file1"
        )), response.error)
    }

    @Test
    fun `append should work`() {
        val testDir = rootDir.resolve("dir1").createDirectory()
        testDir.resolve("file1").createFile()
        val data = RANDOM.nextBytes(1234)

        val response = client.append(1, AppendParams("dir1/file1", encoder.encodeToString(data)))

        assertJsonRpcResponse(response, "1")
        val result = response.result!!
        assertEquals(FileInfoDto("file1", 1234, "/dir1/file1", false), result)
        assertArrayEquals(data, testDir.resolve("file1").readBytes())
    }

    @Test
    fun `append should return error on unknown file`() {
        val response = client.append(1, AppendParams("unknown", encoder.encodeToString(RANDOM.nextBytes(1234))))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.NOT_FOUND.copy(data = JsonRpcResponse.Error.Data(
            message = "NoSuchFileException: /unknown"
        )), response.error)
    }

    @Test
    fun `append should return error on dir`() {
        val testDir = rootDir.resolve("dir1").createDirectory()
        testDir.resolve("file1").createFile()
        val response = client.append(1, AppendParams("dir1", encoder.encodeToString(RANDOM.nextBytes(1))))

        assertJsonRpcError(response, "1")
        assertEquals(JsonRpcErrors.BAD_REQUEST.copy(data = JsonRpcResponse.Error.Data(
            message = "FileSystemException: Cannot append to directory"
        )), response.error)
    }

    @Test
    fun `append should return error on oversize request`() {
        val exception = assertThrows(HttpClientResponseException::class.java) {
            client.append(1, AppendParams("dir1/file1",
                encoder.encodeToString(RANDOM.nextBytes(MAX_REQUEST_SIZE + 1))))
        }
        assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, exception.response.status)
        val response = exception.response.getBody(JsonRpcResponse::class.java).orElseThrow()
        assertJsonRpcError(response, null)
        assertEquals(JsonRpcErrors.REQUEST_TOO_LARGE.copy(data = JsonRpcResponse.Error.Data(
            message = "ContentLengthExceededException: The content length [1419] exceeds the maximum allowed" +
                    " content length [1000]"
        )), response.error)
    }
}