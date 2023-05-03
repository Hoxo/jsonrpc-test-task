package com.github.hoxo.fileservice

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.hoxo.fileservice.jsonrpc.JsonRpcFileService
import com.googlecode.jsonrpc4j.JsonRpcServer
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory

@Factory
class BeanFactory {

    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().registerKotlinModule()
    }

    @Bean
    fun jsonRpcServer(objectMapper: ObjectMapper,
                      jsonRpcFileService: JsonRpcFileService): JsonRpcServer {
        return JsonRpcServer(objectMapper, jsonRpcFileService)
    }
}