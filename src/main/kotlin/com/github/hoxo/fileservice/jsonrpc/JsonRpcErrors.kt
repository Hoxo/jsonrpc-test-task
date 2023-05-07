package com.github.hoxo.fileservice.jsonrpc

class JsonRpcErrors {
    companion object {
        //client errors
        val BAD_REQUEST = JsonRpcResponse.Error(-32000, "Bad request")
        val NOT_FOUND = JsonRpcResponse.Error(-32001, "Not found")
        val CONFLICT = JsonRpcResponse.Error(-32002, "Conflict")
        val REQUEST_TOO_LARGE = JsonRpcResponse.Error(-32003, "Request is too large")
        val TOO_MANY_REQUESTS = JsonRpcResponse.Error(-32004, "Too many requests")

        //server errors
        val INTERNAL_ERROR = JsonRpcResponse.Error(-32603, "Internal error")
    }
}