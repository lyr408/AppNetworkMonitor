package com.lyr.network.monitor

enum class HttpEventStep {
    begin, dnsStart, connectStart, secureConnectStart, connectionAcquired, requestHeadersStart, responseHeadersStart, requestBodyStart, responseBodyStart, callStart;

    val clientStatusCode: Int
        get() = 0
}