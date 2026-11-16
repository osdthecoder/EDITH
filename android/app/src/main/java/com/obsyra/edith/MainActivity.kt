package com.obsyra.edith

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import java.io.OutputStream

/** Serves the bundled web app from assets/www with correct MIME types. */
class AssetHandler(private val ctx: Context) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val p = if (path.isEmpty() || path.endsWith("/")) path + "index.html" else path
        return try {
            val stream = ctx.assets.open("www/$p")
            val mime = when (p.substringAfterLast('.', "").lowercase()) {
                "html" -> "text/html"
                "js", "mjs" -> "text/javascript"
                "css" -> "text/css"
                "json" -> "application/json"
                "wasm" -> "application/wasm"
                "png" -> "image/png"
                "svg" -> "image/svg+xml"
                "woff2" -> "font/woff2"
                "txt", "md" -> "text/plain"
                else -> "application/octet-stream"
            }
            val enc = if (mime.startsWith("text") || mime.contains("json")) "utf-8" else null
            WebResourceResponse(mime, enc, stream)
        } catch (e: Exception) {
            null
        }
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var pending: ValueCallback<Array<Uri>>? = null
    private val streams = HashMap<Int, OutputStream>()
    private val uris = HashMap<Int, Uri>()
    private var nextId = 1

    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        pending?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        pending = null
    }

    inner class Bridge {
        @JavascriptInterface
        fun begin(name: String, mime: String): Int {
            return try {
                val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safe)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EDITH")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return -1
                val os = contentResolver.openOutputStream(uri) ?: return -1
                val id = nextId++
                streams[id] = os
                uris[id] = uri
                id
            } catch (e: Exception) {
                -1
            }
        }

        @JavascriptInterface
        fun chunk(id: Int, b64: String): Boolean = try {
            streams[id]!!.write(Base64.decode(b64, Base64.DEFAULT)); true
        } catch (e: Exception) {
            false
        }

        @JavascriptInterface
        fun finish(id: Int): Boolean = try {
            streams.remove(id)?.close()
            val cv = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            contentResolver.update(uris.remove(id)!!, cv, null, null)
            true
        } catch (e: Exception) {
            false
        }

        @JavascriptInterface
        fun abort(id: Int) {
            try {
                streams.remove(id)?.close()
                uris.remove(id)?.let { contentResolver.delete(it, null, null) }
            } catch (e: Exception) {
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        val loader = WebViewAssetLoader.Builder().addPathHandler("/", AssetHandler(this)).build()
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != "appassets.androidplatform.net"
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                w: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                pending?.onReceiveValue(null)
                pending = cb
                return try {
                    chooser.launch(params.createIntent()); true
                } catch (e: Exception) {
                    pending = null; false
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript(
                    "(function(){try{if(typeof tool!=='undefined'&&tool){go(view);return 1}" +
                        "if(typeof view!=='undefined'&&view!=='home'){go('home');return 1}}catch(e){}return 0})()"
                ) { r -> if (r != "1") finish() }
            }
        })
        web.loadUrl("https://appassets.androidplatform.net/index.html")
    }
}
