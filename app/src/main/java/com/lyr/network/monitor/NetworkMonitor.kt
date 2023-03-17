package com.lyr.network.monitor

import android.text.TextUtils
import okhttp3.*
import okio.Buffer
import org.json.JSONException
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class NetworkMonitor internal constructor(
    private val subEventListener: EventListener?,
    private val stepCallback: UploadEventListener?
) : EventListener() {
    private val callStartNanos: Long
    private var dnsStartNano: Long = 0
    private var connectStartNano: Long = 0
    private var requestStartNano: Long = 0
    private val mHttpData = HttpData()

    internal class XYFactory(
        private val subFactory: Factory?,
        private val mStepCallback: UploadEventListener
    ) : Factory {
        override fun create(call: Call): EventListener {
            var subListener: EventListener? = null
            if (subFactory != null) {
                subListener = subFactory.create(call)
            }
            return if (HttpMonitor.isLocalhost(call.request().url.host)) {
                NetworkMonitor(subListener, null)
            } else NetworkMonitor(subListener, mStepCallback)
        }
    }

    init {
        callStartNanos = System.nanoTime()
    }

    override fun dnsStart(call: Call, domainName: String) {
        mHttpData.stepCode = HttpEventStep.dnsStart
        subEventListener?.dnsStart(call, domainName)
        dnsStartNano = System.nanoTime()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        subEventListener?.dnsEnd(call, domainName, inetAddressList)
        if (dnsStartNano <= 0) {
            return
        }
        val cost = getCost(dnsStartNano)
        if (cost < 0) {
            return
        }
        if (stepCallback != null) {
            mHttpData.dnsCost = cost
        }
        dnsStartNano = 0
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        mHttpData.stepCode = HttpEventStep.connectStart
        subEventListener?.connectStart(call, inetSocketAddress, proxy)
        connectStartNano = System.nanoTime()
    }

    override fun secureConnectStart(call: Call) {
        mHttpData.stepCode = HttpEventStep.secureConnectStart
        subEventListener?.secureConnectStart(call)
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        subEventListener?.secureConnectEnd(call, handshake)
    }

    override fun connectEnd(
        call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy,
        protocol: Protocol?
    ) {
        subEventListener?.connectEnd(call, inetSocketAddress, proxy, protocol)
        if (connectStartNano <= 0) {
            return
        }
        val cost = getCost(connectStartNano)
        if (cost <= 0) {
            return
        }
        if (stepCallback != null) {
            mHttpData.proxy = if (proxy == null) null else proxy.toString()
            mHttpData.inetSocketAddress =
                if (inetSocketAddress == null) null else inetSocketAddress.toString()
            mHttpData.protocol = protocol?.toString()
            mHttpData.connectCost = cost
        }
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        mHttpData.stepCode = HttpEventStep.connectionAcquired
        subEventListener?.connectionAcquired(call, connection)
        requestStartNano = System.nanoTime()
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        log("connectionReleased")
        subEventListener?.connectionReleased(call, connection)
        if (requestStartNano <= 0) {
            return
        }
        val cost = getCost(requestStartNano)
        if (cost <= 0) {
            return
        }
        if (stepCallback != null) {
            mHttpData.responseCost = cost
        }
        requestStartNano = 0
    }

    override fun connectFailed(
        call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy,
        protocol: Protocol?, ioe: IOException
    ) {
        super.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
        log("connectFailed")
        subEventListener?.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
    }

    override fun requestHeadersStart(call: Call) {
        super.requestHeadersStart(call)
        mHttpData.stepCode = HttpEventStep.requestHeadersStart
        subEventListener?.requestHeadersStart(call)
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        super.requestHeadersEnd(call, request)
        mHttpData.requestHeaders = request.headers.toString()
        subEventListener?.requestHeadersEnd(call, request)
    }

    override fun responseHeadersStart(call: Call) {
        super.responseHeadersStart(call)
        mHttpData.stepCode = HttpEventStep.responseHeadersStart
        subEventListener?.responseHeadersStart(call)
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        super.responseHeadersEnd(call, response)
        subEventListener?.responseHeadersEnd(call, response)
        mHttpData.responseCode = response.code
        if (mHttpData.responseCode != 200) {
            try {
                val errorJsonString = getErrorJsonString(response)
                mHttpData.errorMsg = errorJsonString
                mHttpData.errorCode = getErrorCode(errorJsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun requestBodyStart(call: Call) {
        super.requestBodyStart(call)
        mHttpData.stepCode = HttpEventStep.requestBodyStart
        subEventListener?.requestBodyStart(call)
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        super.requestBodyEnd(call, byteCount)
        mHttpData.requestByteCount = byteCount
        subEventListener?.requestBodyEnd(call, byteCount)
    }

    override fun responseBodyStart(call: Call) {
        super.responseBodyStart(call)
        mHttpData.stepCode = HttpEventStep.responseBodyStart
        subEventListener?.responseBodyStart(call)
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        super.responseBodyEnd(call, byteCount)
        mHttpData.responseByteCount = byteCount
        subEventListener?.requestBodyEnd(call, byteCount)
    }

    override fun callStart(call: Call) {
        super.callStart(call)
        mHttpData.stepCode = HttpEventStep.callStart
        log("callStart")
        subEventListener?.callStart(call)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        super.callFailed(call, ioe)
        log("callFailed")
        subEventListener?.callFailed(call, ioe)
        if (callStartNanos <= 0) {
            return
        }
        val totalCost = getCost(callStartNanos)
        if (totalCost <= 0) {
            return
        }
        mHttpData.updateByCall(call)
        if (MonitorBlackList.inBlackList(mHttpData.url)) {
            return
        }
        mHttpData.totalCost = totalCost
        mHttpData.errorMsg = "callFailed"
        if (ioe != null) {
            val stringBuilder = StringBuilder()
            stringBuilder.append(mHttpData.stepCode.name)
                .append(",Ex:")
                .append(ioe.javaClass.simpleName)
                .append(",Msg:")
                .append(ioe.message)
                .append(",trace:")
            val stackTraceElements = ioe.stackTrace
            if (stackTraceElements != null && stackTraceElements.size > 0) {
                stringBuilder.append(stackTraceElements[0].toString())
            }
            mHttpData.errorMsg = stringBuilder.toString()
        }
    }

    override fun callEnd(call: Call) {
        super.callEnd(call)
        log("callEnd")
        subEventListener?.callEnd(call)
        mHttpData.updateByCall(call)
        if (MonitorBlackList.inBlackList(mHttpData.url)) {
            return
        }
        if (callStartNanos <= 0) {
            return
        }
        val totalCost = getCost(callStartNanos)
        if (totalCost <= 0) {
            return
        }
        mHttpData.methodName = call.request().url.encodedPath
        mHttpData.method = call.request().method
        mHttpData.totalCost = totalCost
        try {
            mHttpData.requestParams = getRequestParams(call.request())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCost(startNano: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano)
    }

    private fun getErrorCode(errorJsonStr: String?): Int {
        if (!TextUtils.isEmpty(errorJsonStr)) {
            val errorJson: JSONObject
            try {
                errorJson = JSONObject(errorJsonStr)
                return errorJson.optInt("errorCode")
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return 0
    }

    @Throws(Exception::class)
    private fun getErrorJsonString(response: Response): String? {
        val responseBody = response.body
        if (responseBody != null && response.code != 200) {
            val source = responseBody.source()
            // Buffer the entire body.
            try {
                source.request(Long.MAX_VALUE)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val buffer = source.buffer()
            var charset = UTF8
            val contentType = responseBody.contentType()
            if (contentType != null) {
                charset = contentType.charset(UTF8)
            }
            if (isPlaintext(buffer) && charset != null) {
                return String(buffer.clone().readByteArray(), charset)
            }
        }
        return null
    }

    private fun log(message: String) {}

    companion object {
        const val TAG = "NetworkMonitor"
        private val UTF8 = StandardCharsets.UTF_8
        const val TIME_OUT_MILLS = 60 * 1000
        private fun isPlaintext(buffer: Buffer): Boolean {
            return try {
                val prefix = Buffer()
                val byteCount = if (buffer.size < 64) buffer.size else 64
                buffer.copyTo(prefix, 0, byteCount)
                for (i in 0..15) {
                    if (prefix.exhausted()) {
                        break
                    }
                    val codePoint = prefix.readUtf8CodePoint()
                    if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                        return false
                    }
                }
                true
            } catch (e: EOFException) {
                // Truncated UTF-8 sequence.
                false
            }
        }

        @Throws(Exception::class)
        private fun getRequestParams(request: Request): String? {
            val requestBody = request.body
            val hasRequestBody = requestBody != null
            if (!hasRequestBody) {
                return null
            }
            val buffer = Buffer()
            requestBody!!.writeTo(buffer)
            var charset = UTF8
            val contentType = requestBody.contentType()
            if (contentType != null) {
                charset = contentType.charset(UTF8)
            }
            var param: String? = null
            if (isPlaintext(buffer) && charset != null) {
                param = URLDecoder.decode(String(buffer.readByteArray(), charset))
            }
            return param
        }
    }
}