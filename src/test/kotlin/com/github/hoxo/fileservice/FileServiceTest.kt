package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.buffer.BufferAllocator
import com.github.hoxo.fileservice.buffer.SimpleBufferAllocator
import com.github.hoxo.fileservice.model.FileInfo
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
private const val LINUX_DIR_SIZE = 4096L
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
        val testDir = rootDir.resolve("list").createDirectory()
        testDir.resolve("file1").createFile()
        testDir.resolve("file2").createFile()
        testDir.resolve("dir1/dir2").createDirectories()
        val listR = fileService.list("list")
        assertTrue(listR.isSuccess)
        val list = listR.getOrThrow().toList()
        assertEquals(3, list.size)
        list.let {
            assertTrue(it.contains(FileInfo("file1", "file1", 0, false)))
            assertTrue(it.contains(FileInfo("file2", "file2", 0, false)))
            assertTrue(it.contains(FileInfo("dir1", "dir1", LINUX_DIR_SIZE, true)))
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
        assertTrue(rootDir.resolve("test").exists())
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
        assertTrue(rootDir.resolve("test").exists())
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
        rootDir.resolve("test").createFile()
        val createR = fileService.createEmptyFile("test")
        assertTrue(createR.isFailure)
        assertEquals(FileAlreadyExistsException::class, createR.exceptionOrNull()!!::class)
    }

    @Test
    fun `createEmptyDir should work`(): Unit = runBlocking {
        val createR = fileService.createEmptyDir("test")
        assertTrue(createR.isSuccess)
        assertTrue(rootDir.resolve("test").exists())
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
        assertTrue(rootDir.resolve("test").exists())
        val result = createR.getOrThrow()
        assertEquals(LINUX_DIR_SIZE, result.size)
        assertTrue(result.isDirectory)
        assertEquals("test", result.name)
        assertEquals("/test", result.path)
    }

    @Test
    fun `createEmptyDir cannot create file without dir`(): Unit = runBlocking {
        val createR = fileService.createEmptyDir("unknown/test")
        assertTrue(createR.isFailure)
        assertEquals(NoSuchFileException::class, createR.exceptionOrNull()!!::class)
    }

    @Test
    fun `createEmptyDir cannot create duplicate file`(): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        val createR = fileService.createEmptyDir("test")
        assertTrue(createR.isFailure)
        assertEquals(FileAlreadyExistsException::class, createR.exceptionOrNull()!!::class)
    }

    @Test
    fun `delete should work`(): Unit = runBlocking {
        val testFile = rootDir.resolve("test").createFile()
        val deleteR = fileService.delete("test")
        assertTrue(deleteR.isSuccess)
        assertTrue(testFile.notExists())
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `delete cannot delete root dir`(root: String): Unit = runBlocking {
        val deleteR = fileService.delete(root)
        assertTrue(rootDir.exists())
        assertTrue(deleteR.isFailure)
        assertEquals("Cannot delete root dir", deleteR.exceptionOrNull()!!.message)
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `delete won't delete outside root dir`(path: String): Unit = runBlocking {
        val deleteR = fileService.delete(path)
        assertTrue(rootDir.exists())
        assertTrue(deleteR.isFailure)
        assertEquals("Cannot delete root dir", deleteR.exceptionOrNull()!!.message)
    }

    @Test
    fun `delete should fail on unknown path`(): Unit = runBlocking {
        val deleteR = fileService.delete("unknown")
        assertTrue(deleteR.isFailure)
        assertEquals(NoSuchFileException::class, deleteR.exceptionOrNull()!!::class)
    }

    @Test
    fun `delete should fail on non empty dir`(): Unit = runBlocking {
        val testDir = rootDir.resolve("test").createDirectory()
        val testFile = testDir.resolve("testFile").createFile()
        val deleteR = fileService.delete("test")
        assertTrue(deleteR.isFailure)
        assertEquals(DirectoryNotEmptyException::class, deleteR.exceptionOrNull()!!::class)
        assertTrue(testDir.exists())
        assertTrue(testFile.exists())
    }

    @Test
    fun `move should work with file`(): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        val moveR = fileService.move("test", "test2")
        assertTrue(moveR.isSuccess)
        val newPath = moveR.getOrThrow()
        assertEquals("/test2", newPath)
        assertTrue(rootDir.resolve("test2").exists())
        assertTrue(rootDir.resolve("test").notExists())
    }

    @Test
    fun `move should work with dir`(): Unit = runBlocking {
        rootDir.resolve("test").createDirectory()
        val moveR = fileService.move("test", "test2")
        assertTrue(moveR.isSuccess)
        val newPath = moveR.getOrThrow()
        assertEquals("/test2", newPath)
        assertTrue(rootDir.resolve("test2").exists())
        assertTrue(rootDir.resolve("test").notExists())
    }

    @Test
    fun `move should transfer dir with its content`(): Unit = runBlocking {
        val dir = rootDir.resolve("test").createDirectory()
        dir.resolve("testFile").createFile()
        val moveR = fileService.move("test", "test2")
        assertTrue(moveR.isSuccess)
        val newPath = moveR.getOrThrow()
        assertEquals("/test2", newPath)
        val newDir = rootDir.resolve("test2")
        assertTrue(newDir.exists())
        assertTrue(newDir.resolve("testFile").exists())
        assertTrue(dir.notExists())
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `move cannot move root dir`(root: String): Unit = runBlocking {
        val moveR = fileService.move(root, "test")
        assertTrue(moveR.isFailure)
        assertEquals("Cannot move root dir", moveR.exceptionOrNull()!!.message)
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `move cannot move to root dir`(root: String): Unit = runBlocking {
        val testDir = rootDir.resolve("test").createDirectory()
        testDir.resolve("testFile").createFile()
        val moveR = fileService.move("test", root)
        assertTrue(moveR.isFailure)
        assertEquals("Cannot move to root dir", moveR.exceptionOrNull()!!.message)
    }

    @Test
    fun `move cannot move outside file to root dir`(): Unit = runBlocking {
        val tempFile = createTempFile("test")
        val tempFilePath = tempFile.relativeTo(rootDir).toString()
        val moveR = fileService.move(tempFilePath, "/test")
        assertTrue(moveR.isFailure)
        assertEquals(NoSuchFileException::class, moveR.exceptionOrNull()!!::class)
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `move cannot move file to outside dir`(parent: String): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        val moveR = fileService.move("test", "$parent/test2")
        assertTrue(moveR.isSuccess)
        val newPath = moveR.getOrThrow()
        assertEquals("/test2", newPath)
    }

    @Test
    fun `move cannot move unknown file`(): Unit = runBlocking {
        val moveR = fileService.move("unknown", "test")
        assertTrue(moveR.isFailure)
        assertEquals(NoSuchFileException::class, moveR.exceptionOrNull()!!::class)
    }

    @Test
    fun `copy should work with file`(): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        val moveR = fileService.copy("test", "test2")
        assertTrue(moveR.isSuccess)
        val newPath = moveR.getOrThrow()
        assertEquals("/test2", newPath)
        assertTrue(rootDir.resolve("test2").exists())
        assertTrue(rootDir.resolve("test").exists())
    }

    @Test
    fun `copy should work with dir`(): Unit = runBlocking {
        rootDir.resolve("test").createDirectory()
        val moveR = fileService.copy("test", "test2")
        assertTrue(moveR.isSuccess)
        val newPath = moveR.getOrThrow()
        assertEquals("/test2", newPath)
        assertTrue(rootDir.resolve("test2").exists())
        assertTrue(rootDir.resolve("test").exists())
    }

    @Test
    fun `copy should copy dir with its content`(): Unit = runBlocking {
        val dir = rootDir.resolve("test").createDirectory()
        dir.resolve("testFile").createFile()
        val moveR = fileService.copy("test", "test2")
        assertTrue(moveR.isSuccess)
        val newPath = moveR.getOrThrow()
        assertEquals("/test2", newPath)
        val newDir = rootDir.resolve("test2")
        assertTrue(newDir.exists())
        assertTrue(newDir.resolve("testFile").exists())
        assertTrue(dir.exists())
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `copy cannot copy root dir`(root: String): Unit = runBlocking {
        val moveR = fileService.move(root, "test")
        assertTrue(moveR.isFailure)
        assertEquals("Cannot move root dir", moveR.exceptionOrNull()!!.message)
    }

    @MethodSource("rootRoutes")
    @ParameterizedTest
    fun `copy cannot copy to root dir`(root: String): Unit = runBlocking {
        val testDir = rootDir.resolve("test").createDirectory()
        testDir.resolve("testFile").createFile()
        val moveR = fileService.copy("test", root)
        assertTrue(moveR.isFailure)
        assertEquals("Cannot copy to root dir", moveR.exceptionOrNull()!!.message)
    }

    @Test
    fun `copy cannot copy outside file to root dir`(): Unit = runBlocking {
        val tempFile = createTempFile("test")
        val tempFilePath = tempFile.relativeTo(rootDir).toString()
        val moveR = fileService.copy(tempFilePath, "/test")
        assertTrue(moveR.isFailure)
        assertEquals(NoSuchFileException::class, moveR.exceptionOrNull()!!::class)
    }

    @MethodSource("parentRoutes")
    @ParameterizedTest
    fun `copy cannot copy file to outside dir`(parent: String): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        val moveR = fileService.copy("test", "$parent/test2")
        assertTrue(moveR.isSuccess)
        val newPath = moveR.getOrThrow()
        assertEquals("/test2", newPath)
    }

    @Test
    fun `copy cannot copy unknown file`(): Unit = runBlocking {
        val moveR = fileService.copy("unknown", "test")
        assertTrue(moveR.isFailure)
        assertEquals(NoSuchFileException::class, moveR.exceptionOrNull()!!::class)
    }

    @Test
    fun `copy cannot copy file to itself`(): Unit = runBlocking {
        rootDir.resolve("test").createFile()
        val moveR = fileService.copy("test", "test")
        assertTrue(moveR.isFailure)
        assertEquals(IllegalArgumentException::class, moveR.exceptionOrNull()!!::class)
        assertEquals("Cannot copy to the same path", moveR.exceptionOrNull()!!.message)
    }

    @Test
    fun `readFile should validate input params`(): Unit = runBlocking {
        var result = fileService.readFile("test", -1, 1)
        assertTrue(result.isFailure)
        assertEquals(IllegalArgumentException::class, result.exceptionOrNull()!!::class)
        assertEquals("offset must be non-negative", result.exceptionOrNull()!!.message)

        result = fileService.readFile("test", 0, -1)
        assertTrue(result.isFailure)
        assertEquals(IllegalArgumentException::class, result.exceptionOrNull()!!::class)
        assertEquals("toRead must be positive", result.exceptionOrNull()!!.message)

        result = fileService.readFile("test", 0, MAX_FILE_CHUNK_SIZE + 1)
        assertTrue(result.isFailure)
        assertEquals(IllegalArgumentException::class, result.exceptionOrNull()!!::class)
        assertEquals("toRead must be less than $MAX_FILE_CHUNK_SIZE bytes", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `readFile should read file`(): Unit = runBlocking {
        val testFile = rootDir.resolve("test").createFile()
        val data = RANDOM.nextBytes(BUFFER_SIZE * 7 + 1)
        testFile.writeBytes(data)

        val result = fileService.readFile("test", 0, data.size)
        assertTrue(result.isSuccess)
        val chunk = result.getOrThrow()
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

        val result = fileService.readFile("test", 0, MAX_FILE_CHUNK_SIZE)
        assertTrue(result.isSuccess)
        val chunk = result.getOrThrow()
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
        val result = fileService.readFile("test", offset.toLong(), MAX_FILE_CHUNK_SIZE)
        assertTrue(result.isSuccess)
        val chunk = result.getOrThrow()
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

        val result = fileService.readFile("test", 0, data.size - 1)
        assertTrue(result.isSuccess)
        val chunk = result.getOrThrow()
        assertEquals(data.size - 1, chunk.size)
        assertArrayEquals(data.sliceArray(IntRange(0, data.size - 2)), chunk.data)
        assertTrue(chunk.hasRemainingData)
        assertEquals(0, chunk.offset)
        assertEquals(FileInfo("test", "/test", data.size.toLong(), false), chunk.info)
    }

    @Test
    fun `readFile cannot read dir`(): Unit = runBlocking {
        rootDir.resolve("testDir").createDirectory()
        val result = fileService.readFile("testDir", 0, 1)
        assertTrue(result.isFailure)
        assertEquals(IOException::class, result.exceptionOrNull()!!::class)
        assertEquals("Is a directory", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `readFile cannot unknown file`(): Unit = runBlocking {
        val result = fileService.readFile("unknown", 0, 1)
        assertTrue(result.isFailure)
        assertEquals(NoSuchFileException::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `readFile cannot read parent files`(): Unit = runBlocking {
        val tmpFile = createTempFile("test")
        val result = fileService.readFile(tmpFile.relativeTo(rootDir).toString(), 0, 1)
        assertTrue(result.isFailure)
        assertEquals(NoSuchFileException::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `append should work`(): Unit = runBlocking {
        val testFile = rootDir.resolve("test").createFile()

        val appendData = RANDOM.nextBytes(BUFFER_SIZE * 3 + 1)
        val result = fileService.append("test", appendData)
        assertTrue(result.isSuccess)
        val fileInfo = result.getOrThrow()
        assertEquals(FileInfo("test", "/test", appendData.size.toLong(), false), fileInfo)
        val fileData = testFile.readBytes()
        assertArrayEquals(appendData, fileData)

        val appendData2 = RANDOM.nextBytes(BUFFER_SIZE * 3 + 1)
        val result2 = fileService.append("test", appendData2)
        assertTrue(result2.isSuccess)
        val fileInfo2 = result2.getOrThrow()
        assertEquals(FileInfo("test", "/test", appendData.size.toLong() + appendData2.size.toLong(), false), fileInfo2)
        val fileData2 = testFile.readBytes()
        assertArrayEquals(appendData + appendData2, fileData2)
    }

    @Test
    fun `append should validate input params`(): Unit = runBlocking {
        val result = fileService.append("test", ByteArray(0))
        assertTrue(result.isFailure)
        assertEquals(IllegalArgumentException::class, result.exceptionOrNull()!!::class)
        assertEquals("data must not be empty", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `append should not append to dir`(): Unit = runBlocking {
        rootDir.resolve("testDir").createDirectory()
        val result = fileService.append("testDir", ByteArray(1))
        assertTrue(result.isFailure)
        assertEquals(FileSystemException::class, result.exceptionOrNull()!!::class)
        assertEquals("Cannot append to directory", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `append should not append to unknown file`(): Unit = runBlocking {
        val result = fileService.append("unknown", ByteArray(1))
        assertTrue(result.isFailure)
        assertEquals(NoSuchFileException::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `append should not append to parent files`(): Unit = runBlocking {
        val tmpFile = createTempFile("test")
        val result = fileService.append(tmpFile.relativeTo(rootDir).toString(), ByteArray(1))
        assertTrue(result.isFailure)
        assertEquals(NoSuchFileException::class, result.exceptionOrNull()!!::class)
    }
}