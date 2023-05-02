package com.github.hoxo

import io.micronaut.runtime.Micronaut.run
import org.slf4j.LoggerFactory

private val LOG = LoggerFactory.getLogger("app")

fun main(args: Array<String>) {
    val ctx = run(*args)
    Runtime.getRuntime().addShutdownHook(Thread {
        LOG.info("Shutting down application")
        ctx.close()
    })
}

