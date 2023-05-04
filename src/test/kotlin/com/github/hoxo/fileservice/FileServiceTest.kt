package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.buffer.BufferAllocator
import com.github.hoxo.fileservice.buffer.SimpleBufferAllocator
import com.github.hoxo.fileservice.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.absolutePathString

private const val MAX_FILE_CHUNK_SIZE = 10000
private const val LINUX_DIR_SIZE = 4096L

class FileServiceTest {

    companion object {
        @JvmStatic
        private fun parentRoutes() = listOf(
            Arguments.of(".."),
            Arguments.of("/../../../../"),
            Arguments.of("../../../../../.."),
            Arguments.of("/../../"),
        )

        @JvmStatic
        private fun rootRoutes() = listOf(
            Arguments.of(""),
            Arguments.of("/"),
            Arguments.of("."),
        )
    }

    private lateinit var fileService: FileService
    private lateinit var bufferAllocator: BufferAllocator
    private lateinit var tmpDir: Path

    @BeforeEach
    fun setup() {
        val config = Config.SimpleAllocator(enabled = true, bufferSize = 1000)
        bufferAllocator = Mockito.spy(SimpleBufferAllocator(config))
        tmpDir = Files.createTempDirectory("file-service-test-")
        fileService = FileServiceImpl(bufferAllocator, Config(tmpDir.absolutePathString(), MAX_FILE_CHUNK_SIZE))
    }

    @AfterEach
    fun teardown() {
        tmpDir.toFile().deleteRecursively()
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `getInfo should return info about root dir`(path: String): Unit = runBlocking {
        val infoR = fileService.getInfo(path)
        assertTrue(infoR.isSuccess)
        val info = infoR.getOrThrow()
        assertEquals("/", info.path)
        assertEquals(LINUX_DIR_SIZE, info.size)
        assertTrue(info.isDirectory)
        assertEquals("", info.name)
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `getInfo cannot get outside of root dir`(path: String): Unit = runBlocking {
        val infoR = fileService.getInfo(path)
        assertTrue(infoR.isSuccess)
        val info = infoR.getOrThrow()
        assertEquals("/", info.path)
        assertEquals(LINUX_DIR_SIZE, info.size)
        assertTrue(info.isDirectory)
        assertEquals("", info.name)
    }

    @Test
    fun `getInfo should fail on unknown path`(): Unit = runBlocking {
        val infoR = fileService.getInfo("unknown")
        assertTrue(infoR.isFailure)
        assertEquals(NoSuchFileException::class, infoR.exceptionOrNull()!!::class)
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `list should return empty flow for empty dir`(path: String): Unit = runBlocking {
        val listR = fileService.list(path)
        assertTrue(listR.isSuccess)
        val list = listR.getOrThrow()
        assertEquals(0, list.count())
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `list won't read outside root dir`(path: String): Unit = runBlocking {
        val listR = fileService.list(path)
        assertTrue(listR.isSuccess)
        val list = listR.getOrThrow()
        assertEquals(0, list.count())
    }

    @Test
    fun `list should return existing files and dirs`(): Unit = runBlocking {
        val testDir = tmpDir.resolve("list")
        withContext(Dispatchers.IO) {
            Files.createDirectory(testDir)
            Files.createFile(testDir.resolve("file1"))
            Files.createFile(testDir.resolve("file2"))
            Files.createDirectories(testDir.resolve("dir1/dir2"))

        }
        val listR = fileService.list(tmpDir.relativize(testDir).toString())
        assertTrue(listR.isSuccess)
        val list = listR.getOrThrow().toList()
        assertEquals(3, list.size)
        list.let {
            assertTrue(it.contains(FileInfo("file1", "/file1", 0, false)))
            assertTrue(it.contains(FileInfo("file2", "/file2", 0, false)))
            assertTrue(it.contains(FileInfo("dir1", "/dir1", LINUX_DIR_SIZE, true)))
        }
    }

    @Test
    fun `list will fail on unknown path`(): Unit = runBlocking {
        val listR = fileService.list("unknown")
        assertTrue(listR.isFailure)
        assertEquals(NoSuchFileException::class, listR.exceptionOrNull()!!::class)
    }

    @Test
    fun `createEmptyFile should work`(): Unit = runBlocking {
        val createR = fileService.createEmptyFile("test")
        assertTrue(createR.isSuccess)
        assertTrue(Files.exists(tmpDir.resolve("test")))
        val result = createR.getOrThrow()
        assertEquals(0, result.size)
        assertFalse(result.isDirectory)
        assertEquals("test", result.name)
        assertEquals("/test", result.path)
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `createEmptyFile won't create file outside root dir`(path: String): Unit = runBlocking {
        val createR = fileService.createEmptyFile("$path/test")
        assertTrue(createR.isSuccess)
        assertTrue(Files.exists(tmpDir.resolve("test")))
        val result = createR.getOrThrow()
        assertEquals(0, result.size)
        assertFalse(result.isDirectory)
        assertEquals("test", result.name)
        assertEquals("/test", result.path)
    }

    @Test
    fun `createEmptyFile cannot create file without dir`(): Unit = runBlocking {
        val createR = fileService.createEmptyFile("unknown/test")
        assertTrue(createR.isFailure)
        assertEquals(NoSuchFileException::class, createR.exceptionOrNull()!!::class)
    }

    @Test
    fun `createEmptyFile cannot create duplicate file`(): Unit = runBlocking {
        withContext(Dispatchers.IO) {
            Files.createFile(tmpDir.resolve("test"))
        }
        val createR = fileService.createEmptyFile("test")
        assertTrue(createR.isFailure)
        assertEquals(FileAlreadyExistsException::class, createR.exceptionOrNull()!!::class)
    }

    @Test
    fun `createEmptyDir should work`(): Unit = runBlocking {
        val createR = fileService.createEmptyDir("test")
        assertTrue(createR.isSuccess)
        assertTrue(Files.exists(tmpDir.resolve("test")))
        val result = createR.getOrThrow()
        assertEquals(LINUX_DIR_SIZE, result.size)
        assertTrue(result.isDirectory)
        assertEquals("test", result.name)
        assertEquals("/test", result.path)
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `createEmptyDir won't create dir outside root dir`(path: String): Unit = runBlocking {
        val createR = fileService.createEmptyDir("$path/test")
        assertTrue(createR.isSuccess)
        assertTrue(Files.exists(tmpDir.resolve("test")))
        val result = createR.getOrThrow()
        assertEquals(LINUX_DIR_SIZE, result.size)
        assertTrue(result.isDirectory)
        assertEquals("test", result.name)
        assertEquals("/test", result.path)
    }
}