package com.github.hoxo.fileservice

import com.github.hoxo.fileservice.buffer.escapePath
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class UtilsTest {
    companion object {
        @JvmStatic
        fun escapePathTestCases() = listOf(
            Arguments.of("", "/"),
            Arguments.of(".", "/"),
            Arguments.of("a", "/a"),
            Arguments.of("a/b/c", "/a/b/c"),
            Arguments.of("/a/b/c", "/a/b/c"),
            Arguments.of("/a/b/../c", "/a/c"),
            Arguments.of("/a/b/../../c", "/c"),
            Arguments.of("/a/b/../../c/..", "/"),
            Arguments.of("a/..", "/"),
            Arguments.of("a/../..", "/"),
            Arguments.of("a/../../", "/"),
            Arguments.of("./././././.", "/"),
            Arguments.of("/././././././", "/"),
            Arguments.of("/../../../../../", "/"),
            Arguments.of("../../../../../../", "/"),
        )
    }

    @MethodSource("escapePathTestCases")
    @ParameterizedTest
    fun `escapePath test`(input: String, expected: String) {
        escapePath(input).also { Assertions.assertEquals(expected, it) }
    }
}