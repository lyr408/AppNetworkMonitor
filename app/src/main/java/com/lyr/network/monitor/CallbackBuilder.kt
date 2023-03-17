package com.lyr.network.monitor

import okhttp3.EventListener

class CallbackBuilder {
    lateinit var factory: EventListener.Factory
    lateinit var stepCallback: UploadEventListener
}