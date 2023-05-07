package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.buffer.BufferAllocator
import com.github.hoxo.fileservice.buffer.SimpleBufferAllocator
import com.github.hoxo.fileservice.model.FileInfo
import com.github.hoxo.fileservice.service.FileService
import com.github.hoxo.fileservice.service.FileServiceImpl
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.IOException
import java.nio.file.*
import kotlin.io.path.*
import kotlin.random.Random

private const val MAX_FILE_CHUNK_SIZE = 10000
const val LINUX_DIR_SIZE = 4096L
private const val RANDOM_SEED = 42L
private const val BUFFER_SIZE = 1000

private val RANDOM = Random(RANDOM_SEED)

class FileServiceTest {

    companion object {
        @JvmStatic
        private fun parentRoutes() = listOf(
            Arguments.of(".."),
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
    private lateinit var rootDir: Path

    @BeforeEach
    fun setup() {
        val config = Config.SimpleAllocator(enabled = true, bufferSize = BUFFER_SIZE)
        bufferAllocator = SimpleBufferAllocator(config)
        rootDir = Files.createTempDirectory("file-service-test-")
        fileService = FileServiceImpl(bufferAllocator, Config(rootDir.absolutePathString(), MAX_FILE_CHUNK_SIZE))
    }

    @AfterEach
    fun teardown() {
        rootDir.toFile().deleteRecursively()
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `getInfo should return info about root dir`(path: String): Unit = runBlocking {
        val info = fileService.getInfo(path)
        assertEquals("/", info.path)
        assertEquals(LINUX_DIR_SIZE, info.size)
        assertTrue(info.isDirectory)
        assertEquals("", info.name)
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `getInfo cannot get outside of root dir`(path: String): Unit = runBlocking {
        val info = fileService.getInfo(path)
        assertEquals("/", info.path)
        assertEquals(LINUX_DIR_SIZE, info.size)
        assertTrue(info.isDirectory)
        assertEquals("", info.name)
    }

    @Test
    fun `getInfo should fail on unknown path`(): Unit = runBlocking {
        assertThrowsSuspend<NoSuchFileException> {
            fileService.getInfo("unknown")
        }
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `list should return empty flow for empty dir`(path: String): Unit = runBlocking {
        val list = fileService.list(path)
        assertEquals(0, list.count())
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `list won't read outside root dir`(path: String): Unit = runBlocking {
        val list = fileService.list(path)
        assertEquals(0, list.count())
    }

    @Test
    fun `list should return existing files and dirs`(): Unit = runBlocking {
        val testDir = rootDir.resolve("list").createDirectory()
        testDir.resolve("file1").createFile()
        testDir.resolve("file2").createFile()
        testDir.resolve("dir1/dir2").createDirectories()
        val list = fileService.list("list").toList()
        assertEquals(3, list.size)
        list.let {
            assertTrue(it.contains(FileInfo("file1", "/list/file1", 0, false)))
            assertTrue(it.contains(FileInfo("file2", "/list/file2", 0, false)))
            assertTrue(it.contains(FileInfo("dir1", "/list/dir1", LINUX_DIR_SIZE, true)))
        }
    }

    @Test
    fun `list will fail on unknown path`(): Unit = runBlocking {
        assertThrowsSuspend<NoSuchFileException> {
            fileService.list("unknown")
        }
    }

    @Test
    fun `createEmptyFile should work`(): Unit = runBlocking {
        val result = fileService.createEmptyFile("test")
        assertTrue(rootDir.resolve("test").exists())
        assertEquals(0, result.size)
        assertFalse(result.isDirectory)
        assertEquals("test", result.name)
        assertEquals("/test", result.path)
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `createEmptyFile won't create file outside root dir`(path: String): Unit = runBlocking {
        val result = fileService.createEmptyFile("$path/test")
        assertTrue(rootDir.resolve("test").exists())
        assertEquals(0, result.size)
        assertFalse(result.isDirectory)
        assertEquals("test", result.name)
        assertEquals("/test", result.path)
    }

    @Test
    fun `createEmptyFile cannot create file without dir`(): Unit = runBlocking {
        assertThrowsSuspend<NoSuchFileException> {
            fileService.createEmptyFile("unknown/test")
        }
    }

    @Test
    fun `createEmptyFile cannot create duplicate file`(): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        assertThrowsSuspend<FileAlreadyExistsException> {
            fileService.createEmptyFile("test")
        }
    }

    @Test
    fun `createEmptyDir should work`(): Unit = runBlocking {
        val result = fileService.createEmptyDir("test")
        assertTrue(rootDir.resolve("test").exists())
        assertEquals(LINUX_DIR_SIZE, result.size)
        assertTrue(result.isDirectory)
        assertEquals("test", result.name)
        assertEquals("/test", result.path)
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `createEmptyDir won't create dir outside root dir`(path: String): Unit = runBlocking {
        val result = fileService.createEmptyDir("$path/test")
        assertTrue(rootDir.resolve("test").exists())
        assertEquals(LINUX_DIR_SIZE, result.size)
        assertTrue(result.isDirectory)
        assertEquals("test", result.name)
        assertEquals("/test", result.path)
    }

    @Test
    fun `createEmptyDir cannot create file without dir`(): Unit = runBlocking {
        assertThrowsSuspend<NoSuchFileException> {
            fileService.createEmptyDir("unknown/test")
        }
    }

    @Test
    fun `createEmptyDir cannot create duplicate file`(): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        assertThrowsSuspend<FileAlreadyExistsException> {
            fileService.createEmptyDir("test")
        }
    }

    @Test
    fun `delete should work`(): Unit = runBlocking {
        val testFile = rootDir.resolve("test").createFile()
        fileService.delete("test")
        assertTrue(testFile.notExists())
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `delete cannot delete root dir`(root: String): Unit = runBlocking {
        assertThrowsSuspend<IllegalArgumentException> {
            fileService.delete(root)
        }
        assertTrue(rootDir.exists())
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `delete won't delete outside root dir`(path: String): Unit = runBlocking {
        assertThrowsSuspend<IllegalArgumentException> {
            fileService.delete(path)
        }
        assertTrue(rootDir.exists())
    }

    @Test
    fun `delete should fail on unknown path`(): Unit = runBlocking {
        assertThrowsSuspend<NoSuchFileException> {
            fileService.delete("unknown")
        }
    }

    @Test
    fun `delete should fail on non empty dir`(): Unit = runBlocking {
        val testDir = rootDir.resolve("test").createDirectory()
        val testFile = testDir.resolve("testFile").createFile()
        assertThrowsSuspend<DirectoryNotEmptyException> {
            fileService.delete("test")
        }
        assertTrue(testDir.exists())
        assertTrue(testFile.exists())
    }

    @Test
    fun `move should work with file`(): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        val newPath = fileService.move("test", "test2")
        assertEquals("/test2", newPath)
        assertTrue(rootDir.resolve("test2").exists())
        assertTrue(rootDir.resolve("test").notExists())
    }

    @Test
    fun `move should work with dir`(): Unit = runBlocking {
        rootDir.resolve("test").createDirectory()
        val newPath = fileService.move("test", "test2")
        assertEquals("/test2", newPath)
        assertTrue(rootDir.resolve("test2").exists())
        assertTrue(rootDir.resolve("test").notExists())
    }

    @Test
    fun `move should transfer dir with its content`(): Unit = runBlocking {
        val dir = rootDir.resolve("test").createDirectory()
        dir.resolve("testFile").createFile()
        val newPath = fileService.move("test", "test2")
        assertEquals("/test2", newPath)
        val newDir = rootDir.resolve("test2")
        assertTrue(newDir.exists())
        assertTrue(newDir.resolve("testFile").exists())
        assertTrue(dir.notExists())
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `move cannot move root dir`(root: String): Unit = runBlocking {
        val e = assertThrowsSuspend<IllegalArgumentException> {
            fileService.move(root, "test")
        }
        assertEquals("Cannot move root directory", e.message)
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `move cannot move to root dir`(root: String): Unit = runBlocking {
        val testDir = rootDir.resolve("test").createDirectory()
        testDir.resolve("testFile").createFile()
        val e = assertThrowsSuspend<IllegalArgumentException> {
            fileService.move("test", root)
        }
        assertEquals("Cannot move to root directory", e.message)
    }

    @Test
    fun `move cannot move outside file to root dir`(): Unit = runBlocking {
        val tempFile = createTempFile("test")
        val tempFilePath = tempFile.relativeTo(rootDir).toString()
        assertThrowsSuspend<NoSuchFileException> {
            fileService.move(tempFilePath, "/test")
        }
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `move cannot move file to outside dir`(parent: String): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        val newPath = fileService.move("test", "$parent/test2")
        assertEquals("/test2", newPath)
    }

    @Test
    fun `move cannot move unknown file`(): Unit = runBlocking {
        assertThrowsSuspend<NoSuchFileException> {
            fileService.move("unknown", "test")
        }
    }

    @Test
    fun `copy should work with file`(): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        val newPath = fileService.copy("test", "test2")
        assertEquals("/test2", newPath)
        assertTrue(rootDir.resolve("test2").exists())
        assertTrue(rootDir.resolve("test").exists())
    }

    @Test
    fun `copy should work with dir`(): Unit = runBlocking {
        rootDir.resolve("test").createDirectory()
        val newPath = fileService.copy("test", "test2")
        assertEquals("/test2", newPath)
        assertTrue(rootDir.resolve("test2").exists())
        assertTrue(rootDir.resolve("test").exists())
    }

    @Test
    fun `copy should copy dir with its content`(): Unit = runBlocking {
        val dir = rootDir.resolve("test").createDirectory()
        dir.resolve("testFile").createFile()
        val newPath = fileService.copy("test", "test2")
        assertEquals("/test2", newPath)
        val newDir = rootDir.resolve("test2")
        assertTrue(newDir.exists())
        assertTrue(newDir.resolve("testFile").exists())
        assertTrue(dir.exists())
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `copy cannot copy root dir`(root: String): Unit = runBlocking {
        assertThrowsSuspend<IllegalArgumentException> {
            fileService.move(root, "test")
        }
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `copy cannot copy to root dir`(root: String): Unit = runBlocking {
        val testDir = rootDir.resolve("test").createDirectory()
        testDir.resolve("testFile").createFile()
        assertThrowsSuspend<IllegalArgumentException> {
            fileService.copy("test", root)
        }
    }

    @Test
    fun `copy cannot copy outside file to root dir`(): Unit = runBlocking {
        val tempFile = createTempFile("test")
        val tempFilePath = tempFile.relativeTo(rootDir).toString()
        assertThrowsSuspend<NoSuchFileException> {
            fileService.copy(tempFilePath, "/test")
        }
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `copy cannot copy file to outside dir`(parent: String): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        val newPath = fileService.copy("test", "$parent/test2")
        assertEquals("/test2", newPath)
    }

    @Test
    fun `copy cannot copy unknown file`(): Unit = runBlocking {
        assertThrowsSuspend<NoSuchFileException> {
            fileService.copy("unknown", "test")
        }
    }

    @Test
    fun `copy cannot copy file to itself`(): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        val e = assertThrowsSuspend<IllegalArgumentException> {
            fileService.copy("test", "test")
        }
        assertEquals("Cannot copy to the same path", e.message)
    }

    @Test
    fun `readFile should validate input params`(): Unit = runBlocking {
        var e = assertThrowsSuspend<IllegalArgumentException> {
            fileService.readFile("test", -1, 1)
        }
        assertEquals("offset must be non-negative", e.message)

        e = assertThrowsSuspend<IllegalArgumentException> {
            fileService.readFile("test", 0, -1)
        }
        assertEquals("toRead must be positive", e.message)

        e = assertThrowsSuspend<IllegalArgumentException> {
            fileService.readFile("test", 0, MAX_FILE_CHUNK_SIZE + 1)
        }
        assertEquals("toRead must be less than $MAX_FILE_CHUNK_SIZE bytes", e.message)
    }

    @Test
    fun `readFile should read file`(): Unit = runBlocking {
        val testFile = rootDir.resolve("test").createFile()
        val data = RANDOM.nextBytes(BUFFER_SIZE * 7 + 1)
        testFile.writeBytes(data)

        val chunk = fileService.readFile("test", 0, data.size)
        assertArrayEquals(data, chunk.data)
        assertEquals(data.size, chunk.size)
        assertEquals(0, chunk.offset)
        assertFalse(chunk.hasRemainingData)
        assertEquals(FileInfo("test", "/test", data.size.toLong(), false), chunk.info)
    }

    @Test
    fun `readFile should read only remaining bytes`(): Unit = runBlocking {
        val testFile = rootDir.resolve("test").createFile()
        val data = RANDOM.nextBytes(BUFFER_SIZE * 7 + 1)
        testFile.writeBytes(data)

        val chunk = fileService.readFile("test", 0, MAX_FILE_CHUNK_SIZE)
        assertEquals(data.size, chunk.size)
        assertArrayEquals(data, chunk.data)
        assertFalse(chunk.hasRemainingData)
        assertEquals(0, chunk.offset)
        assertEquals(FileInfo("test", "/test", data.size.toLong(), false), chunk.info)
    }

    @Test
    fun `readFile should read from offset`(): Unit = runBlocking {
        val testFile = rootDir.resolve("test").createFile()
        val data = RANDOM.nextBytes(BUFFER_SIZE * 7 + 1)
        testFile.writeBytes(data)

        val offset = 1234
        val chunk = fileService.readFile("test", offset.toLong(), MAX_FILE_CHUNK_SIZE)
        assertEquals(data.size - offset, chunk.size)
        assertArrayEquals(data.sliceArray(IntRange(offset, data.size - 1)), chunk.data)
        assertFalse(chunk.hasRemainingData)
        assertEquals(offset.toLong(), chunk.offset)
        assertEquals(FileInfo("test", "/test", data.size.toLong(), false), chunk.info)
    }

    @Test
    fun `readFile should be aware of remaining data`(): Unit = runBlocking {
        val testFile = rootDir.resolve("test").createFile()
        val data = RANDOM.nextBytes(BUFFER_SIZE * 7 + 1)
        testFile.writeBytes(data)

        val chunk = fileService.readFile("test", 0, data.size - 1)
        assertEquals(data.size - 1, chunk.size)
        assertArrayEquals(data.sliceArray(IntRange(0, data.size - 2)), chunk.data)
        assertTrue(chunk.hasRemainingData)
        assertEquals(0, chunk.offset)
        assertEquals(FileInfo("test", "/test", data.size.toLong(), false), chunk.info)
    }

    @Test
    fun `readFile cannot read dir`(): Unit = runBlocking {
        rootDir.resolve("testDir").createDirectory()
        val e = assertThrowsSuspend<IOException> {
            fileService.readFile("testDir", 0, 1)
        }
        assertEquals("Is a directory", e.message)
    }

    @Test
    fun `readFile cannot unknown file`(): Unit = runBlocking {
        assertThrowsSuspend<NoSuchFileException> {
            fileService.readFile("unknown", 0, 1)
        }
    }

    @Test
    fun `readFile cannot read parent files`(): Unit = runBlocking {
        val tmpFile = createTempFile("test")
        assertThrowsSuspend<NoSuchFileException> {
            fileService.readFile(tmpFile.relativeTo(rootDir).toString(), 0, 1)
        }
    }

    @Test
    fun `append should work`(): Unit = runBlocking {
        val testFile = rootDir.resolve("test").createFile()

        val appendData = RANDOM.nextBytes(BUFFER_SIZE * 3 + 1)
        val fileInfo = fileService.append("test", appendData)
        assertEquals(FileInfo("test", "/test", appendData.size.toLong(), false), fileInfo)
        val fileData = testFile.readBytes()
        assertArrayEquals(appendData, fileData)

        val appendData2 = RANDOM.nextBytes(BUFFER_SIZE * 3 + 1)
        val fileInfo2 = fileService.append("test", appendData2)
        assertEquals(FileInfo("test", "/test", appendData.size.toLong() + appendData2.size.toLong(), false), fileInfo2)
        val fileData2 = testFile.readBytes()
        assertArrayEquals(appendData + appendData2, fileData2)
    }

    @Test
    fun `append should validate input params`(): Unit = runBlocking {
        val e = assertThrowsSuspend<IllegalArgumentException> {
            fileService.append("test", ByteArray(0))
        }
        assertEquals("data must not be empty", e.message)
    }

    @Test
    fun `append should not append to dir`(): Unit = runBlocking {
        rootDir.resolve("testDir").createDirectory()
        val e = assertThrowsSuspend<FileSystemException> {
            fileService.append("testDir", ByteArray(1))
        }
        assertEquals("Cannot append to directory", e.message)
    }

    @Test
    fun `append should not append to unknown file`(): Unit = runBlocking {
        assertThrowsSuspend<NoSuchFileException> {
            fileService.append("unknown", ByteArray(1))
        }
    }

    @Test
    fun `append should not append to parent files`(): Unit = runBlocking {
        val tmpFile = createTempFile("test")
        assertThrowsSuspend<NoSuchFileException> {
            fileService.append(tmpFile.relativeTo(rootDir).toString(), ByteArray(1))
        }
    }
}

suspend inline fun <reified T : Throwable> assertThrowsSuspend(crossinline block: suspend () -> Unit): T {
    try {
        block()
        return fail("Expected exception ${T::class} but nothing was thrown")
    } catch (e: Throwable) {
        if (e is T) {
            return e
        }
        return fail("Expected exception ${T::class} but ${e::class} was thrown")
    }
}