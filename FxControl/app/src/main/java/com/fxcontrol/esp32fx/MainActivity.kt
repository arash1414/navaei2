package com.fxcontrol.esp32fx

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Landscape full-screen WebView host for the ESP32 FX control UI (assets/index.html).
 * The UI talks to the ESP32 with a WebSocket: ws://192.168.4.1:81
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var cm: ConnectivityManager? = null
    private var netCb: ConnectivityManager.NetworkCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // remembers language and ESP32 address
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")

        bindToWifi()
    }

    /** Keep traffic on the ESP32 Wi-Fi even though it has no internet access. */
    private fun bindToWifi() {
        val c = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm = c
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                c.bindProcessToNetwork(network)
            }
            override fun onLost(network: Network) {
                c.bindProcessToNetwork(null)
            }
        }
        netCb = cb
        try {
            c.requestNetwork(req, cb)
        } catch (_: Exception) {
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun openWifiSettings() {
            runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript("(function(){return window.__back?window.__back():false})()") { r ->
            if (r != "true") finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        try {
            netCb?.let { cm?.unregisterNetworkCallback(it) }
            cm?.bindProcessToNetwork(null)
        } catch (_: Exception) {
        }
        web.destroy()
        super.onDestroy()
    }
}
