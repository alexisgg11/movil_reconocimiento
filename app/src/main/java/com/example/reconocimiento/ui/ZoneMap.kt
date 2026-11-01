package com.example.reconocimiento.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

@Composable
fun ZoneMap(latitude: Double?,longitude: Double?,radius: Double,onPoint: (Double,Double)->Unit,modifier: Modifier=Modifier) {
    val callback=rememberUpdatedState(onPoint)
    AndroidView(factory={ZoneWebView(it){lat,lon->callback.value(lat,lon)}},
        update={it.updateZone(latitude,longitude,radius)},onRelease={it.disposeMap()},modifier=modifier)
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
private class ZoneWebView(context: Context,onPoint: (Double,Double)->Unit) : WebView(context) {
    private var ready=false
    private var current=JSONObject()
    init {
        val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(context)).build()
        settings.javaScriptEnabled=true
        settings.allowFileAccess=false
        settings.allowContentAccess=false
        settings.domStorageEnabled=false
        settings.userAgentString=settings.userAgentString+" Reconocimiento/1.1"
        addJavascriptInterface(object {
            @JavascriptInterface fun onPoint(lat: Double,lon: Double) {
                if(lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0&&lon in -180.0..180.0)post {onPoint(lat,lon)}
            }
        },"ZoneBridge")
        webViewClient=object: WebViewClient() {
            override fun shouldInterceptRequest(view: WebView,request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)?.apply {
                responseHeaders = (responseHeaders ?: emptyMap()) + ("Cache-Control" to "no-store")
            }
            override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest): Boolean {
                if(request.url.host=="appassets.androidplatform.net")return false
                if(request.url.scheme=="https"&&request.url.host=="www.openstreetmap.org")runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,request.url))}
                return true
            }
            override fun onPageFinished(view: WebView,url: String) {ready=true;render()}
        }
        setOnTouchListener {view,event->
            if(event.actionMasked==MotionEvent.ACTION_DOWN)view.parent?.requestDisallowInterceptTouchEvent(true)
            if(event.actionMasked==MotionEvent.ACTION_UP||event.actionMasked==MotionEvent.ACTION_CANCEL)view.parent?.requestDisallowInterceptTouchEvent(false)
            false
        }
        loadUrl("https://appassets.androidplatform.net/assets/map/index.html?v=3")
    }
    fun updateZone(lat: Double?,lon: Double?,radius: Double) {
        current=JSONObject().put("lat",lat?:JSONObject.NULL).put("lon",lon?:JSONObject.NULL).put("radius",radius)
        if(ready)render()
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(ready)post {render()}
    }
    private fun render(){
        val cssHeight=(height/resources.displayMetrics.density).coerceAtLeast(1f)
        evaluateJavascript("window.resizeZone($cssHeight);window.setZone(${current.opt("lat")?:"null"},${current.opt("lon")?:"null"},${current.optDouble("radius",100.0)});",null)
    }
    fun disposeMap(){ready=false;stopLoading();removeJavascriptInterface("ZoneBridge");destroy()}
}
