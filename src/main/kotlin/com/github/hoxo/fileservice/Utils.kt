package com.github.hoxo.fileservice

import java.nio.file.Path

internal fun escapePath(path: String): String {
    return Path.of("/$path")
        .normalize()
        .toString()
        .replace("../", "")
        .replace("/..", "/")
        .replace("..", "")
}