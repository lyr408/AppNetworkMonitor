package com.lyr.network.monitor

import okhttp3.Call

class HttpData {
    var proxy: String? = null
    var inetSocketAddress: String? = null
    var protocol: String? = null

    // http or https
    var schema: String? = null
    var domain: String? = null

    // GET,POST
    var method: String? = null

    // 服务名 api/rest/xxx/xxx
    var methodName: String? = null

    // url
    var url: String? = null

    //DNS的耗时
    var dnsCost: Long? = null

    //建连耗时
    var connectCost: Long? = null

    //服务器响应耗时
    var responseCost: Long = 0

    //网络请求总耗时
    var totalCost: Long = 0

    //TraceId,用于全链路监控
    var traceId: String? = null

    //请求参数
    var requestParams: String? = null

    //错误信息
    var errorMsg: String? = null

    //本地错误码-40,-20
    var stepCode = HttpEventStep.begin

    //Http状态码 200 ,404
    var responseCode: Int? = null

    //小影错误码,无用
    var errorCode = 0

    //请求包体大小
    var requestByteCount: Long = 0

    //响应包体大小
    var responseByteCount: Long = 0

    //请求头
    var requestHeaders: String? = null

    //响应头
    var responseHeaders: String? = null
    fun updateByCall(call: Call) {
        schema = call.request().url.scheme
        domain = call.request().url.host
        method = call.request().method
        methodName = call.request().url.encodedPath
        url = call.request().url.toString()
    }

    val statusCode: String
        get() {
            val statusCode: Int
            statusCode = if (responseCode != null && responseCode != 0) ({
                responseCode
            })!! else {
                stepCode.clientStatusCode
            }
            return statusCode.toString()
        }
    val isFirst: Boolean
        get() = dns() && connect()

    fun dns(): Boolean {
        return dnsCost != null && dnsCost!! >= 0
    }

    fun connect(): Boolean {
        return (connectCost != null
                && connectCost!! >= 0)
    }
}