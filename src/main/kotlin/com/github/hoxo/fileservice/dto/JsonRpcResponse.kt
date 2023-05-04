package com.github.hoxo.fileservice.dto

import com.googlecode.jsonrpc4j.ErrorResolver.JsonError

data class JsonRpcResponse(
    val response: Any? = null,
    val error: JsonError? = null,
    val id: String = "null",
) {
    val jsonrpc = "2.0"
}