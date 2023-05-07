package com.github.hoxo.fileservice.http

import com.github.hoxo.fileservice.Config
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

@Singleton
class FileServicePool(
    config: Config.FileService
) {
    val executor = Executors.newFixedThreadPool(config.poolSize)
    val dispatcher = executor.asCoroutineDispatcher()

    @PreDestroy
    fun shutdown() {
        dispatcher.close()
    }
}