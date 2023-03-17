package com.lyr.network.monitor

interface UploadEventListener {
    fun onKVEvent(eventId: String?, params: HashMap<String?, String?>?)
}