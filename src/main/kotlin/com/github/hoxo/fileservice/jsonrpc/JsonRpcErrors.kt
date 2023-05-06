package com.github.hoxo.fileservice.jsonrpc

class JsonRpcErrors {
    companion object {
        //client errors
        val BAD_REQUEST = JsonRpcResponse.Error(-32000, "Bad request")
        val NOT_FOUND = JsonRpcResponse.Error(-32001, "Not found")
    }
}