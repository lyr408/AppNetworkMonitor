package com.lyr.network.monitor

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import okhttp3.OkHttpClient

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val httpClientBuilder = OkHttpClient.Builder();
        val callbackBuilder = CallbackBuilder();
        val factory = NetworkMonitor.XYFactory(callbackBuilder.factory, callbackBuilder.stepCallback);
        httpClientBuilder.eventListenerFactory(factory);
    }
}