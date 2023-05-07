package com.github.hoxo.fileservice

import java.nio.file.Path

/**
 * Normalize path and remove all `..` from it. Result path will be absolute even if input path was relative.
 * @param path path to normalize
 * @return normalized path
 */
internal fun escapePath(path: String): String {
    return Path.of("/$path")
        .normalize()
        .toString()
        .replace("../", "")
        .replace("/..", "/")
        .replace("..", "")
}