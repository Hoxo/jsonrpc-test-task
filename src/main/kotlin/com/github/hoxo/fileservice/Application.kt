package com.github.hoxo.fileservice

import io.micronaut.runtime.Micronaut.build
import org.slf4j.LoggerFactory

private val LOG = LoggerFactory.getLogger("app")

fun main(args: Array<String>) {
    build(*args)
        .eagerInitSingletons(true)
        .start()
    Runtime.getRuntime().addShutdownHook(Thread {
        LOG.info("Shutting down application")
    })
}

