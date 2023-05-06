package com.github.hoxo.fileservice.jsonrpc

data class JsonRpcResponse<T>(
    val result: T? = null,
    val error: Error? = null,
    val id: String? = null,
) {
    val jsonrpc = "2.0"
    data class Error(
        val code: Int,
        val message: String? = null,
        val data: Data? = null,
    ) {
        data class Data (
            val message: String? = null,
            val details: Map<String, Any>? = null,
        )
    }
}