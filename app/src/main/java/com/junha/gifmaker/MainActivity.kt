package com.junha.gifmaker

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebView.WebViewTransport
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.junha.gifmaker.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pageHadError = false

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        callback.onReceiveValue(uris)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        setupWebView()
        binding.retryButton.setOnClickListener { loadSite() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else {
            loadSite()
        }
    }

    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportZoom(false)
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.userAgentString = settings.userAgentString + " GifMakerApp/1.0"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(JsBridge(), JS_BRIDGE_NAME)
        webView.webViewClient = AppWebViewClient()
        webView.webChromeClient = AppChromeClient()
        webView.setDownloadListener(AppDownloadListener())
    }

    private fun loadSite() {
        pageHadError = false
        binding.errorView.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        if (!isOnline()) {
            showError()
            return
        }
        binding.webView.loadUrl(getString(R.string.site_url))
    }

    private fun showError() {
        binding.progressBar.visibility = View.GONE
        binding.webView.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isAppHost(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host == APP_HOST || host.endsWith(".$APP_HOST")
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            binding.webView.loadUrl(uri.toString())
        }
    }

    private fun injectDownloadHook() {
        binding.webView.evaluateJavascript(DOWNLOAD_HOOK_JS, null)
    }

    private fun guessFileName(
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ): String {
        val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
        return guessed.ifBlank { "gifmaker_${System.currentTimeMillis()}.gif" }
    }

    private fun saveDataUrl(dataUrl: String, filename: String, mimeType: String) {
        Thread {
            try {
                val saved = writeDataUrlToDownloads(dataUrl, filename, mimeType)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.download_saved, saved),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun writeDataUrlToDownloads(
        dataUrl: String,
        filename: String,
        mimeType: String
    ): String {
        val comma = dataUrl.indexOf(',')
        require(comma > 0) { "invalid data url" }
        val meta = dataUrl.substring(0, comma)
        val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        val mime = mimeType.ifBlank {
            Regex("data:([^;]+)").find(meta)?.groupValues?.get(1) ?: "image/gif"
        }
        var name = filename.ifBlank { "gifmaker_${System.currentTimeMillis()}.gif" }
        if (!name.contains('.')) {
            name += when {
                mime.contains("gif") -> ".gif"
                mime.contains("png") -> ".png"
                mime.contains("jpeg") || mime.contains("jpg") -> ".jpg"
                mime.contains("mp4") -> ".mp4"
                else -> ".bin"
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("insert failed")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("stream failed")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return name
        }

        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val file = uniqueFile(dir, name)
        FileOutputStream(file).use { it.write(bytes) }
        return file.name
    }

    private fun uniqueFile(dir: File, name: String): File {
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var file = File(dir, name)
        var i = 1
        while (file.exists()) {
            file = File(dir, "$stem ($i)$ext")
            i++
        }
        return file
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        binding.webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        binding.webView.apply {
            loadUrl("about:blank")
            stopLoading()
            webChromeClient = null
            removeJavascriptInterface(JS_BRIDGE_NAME)
            destroy()
        }
        super.onDestroy()
    }

    inner class JsBridge {
        @JavascriptInterface
        fun saveFile(dataUrl: String, filename: String, mimeType: String) {
            saveDataUrl(dataUrl, filename, mimeType)
        }
    }

    inner class AppWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val uri = request.url
            val scheme = uri.scheme ?: return false
            if (scheme != "http" && scheme != "https") {
                openExternal(uri)
                return true
            }
            if (request.isForMainFrame && !isAppHost(uri)) {
                openExternal(uri)
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            pageHadError = false
            binding.errorView.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
            binding.progressBar.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String?) {
            binding.progressBar.visibility = View.GONE
            injectDownloadHook()
            if (pageHadError && isAppHost(Uri.parse(url ?: return))) {
                showError()
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                pageHadError = true
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                pageHadError = true
            }
        }
    }

    inner class AppChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            binding.progressBar.progress = newProgress
            binding.progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            this@MainActivity.filePathCallback?.onReceiveValue(null)
            this@MainActivity.filePathCallback = filePathCallback
            val intent = fileChooserParams.createIntent().apply {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                if (type.isNullOrBlank()) {
                    type = "*/*"
                }
            }
            return try {
                fileChooserLauncher.launch(
                    Intent.createChooser(intent, getString(R.string.file_chooser_title))
                )
                true
            } catch (_: Exception) {
                this@MainActivity.filePathCallback = null
                filePathCallback.onReceiveValue(null)
                false
            }
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message
        ): Boolean {
            val temp = WebView(view.context)
            temp.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    if (isAppHost(request.url)) {
                        binding.webView.loadUrl(request.url.toString())
                    } else {
                        openExternal(request.url)
                    }
                    return true
                }
            }
            (resultMsg.obj as WebViewTransport).webView = temp
            resultMsg.sendToTarget()
            return true
        }
    }

    inner class AppDownloadListener : DownloadListener {
        override fun onDownloadStart(
            url: String,
            userAgent: String,
            contentDisposition: String,
            mimeType: String,
            contentLength: Long
        ) {
            val filename = guessFileName(url, contentDisposition, mimeType)
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                if (url.startsWith("data:")) {
                    saveDataUrl(url, filename, mimeType)
                } else {
                    val safeUrl = url.replace("\\", "\\\\").replace("\"", "\\\"")
                    val safeName = filename.replace("\\", "\\\\").replace("\"", "\\\"")
                    val safeMime = mimeType.replace("\\", "\\\\").replace("\"", "\\\"")
                    val js = """
                        (async function() {
                          try {
                            const res = await fetch("$safeUrl");
                            const blob = await res.blob();
                            const reader = new FileReader();
                            reader.onloadend = function() {
                              $JS_BRIDGE_NAME.saveFile(reader.result, "$safeName", blob.type || "$safeMime");
                            };
                            reader.readAsDataURL(blob);
                          } catch (e) {}
                        })();
                    """.trimIndent()
                    binding.webView.evaluateJavascript(js, null)
                }
                return
            }

            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    setTitle(filename)
                    setDescription(getString(R.string.app_name))
                }
                getSystemService(DownloadManager::class.java).enqueue(request)
                Toast.makeText(this@MainActivity, R.string.download_started, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                openExternal(Uri.parse(url))
            }
        }
    }

    companion object {
        private const val APP_HOST = "0405junha-debug.github.io"
        private const val JS_BRIDGE_NAME = "GifMakerApp"
        private val DOWNLOAD_HOOK_JS = """
            (function() {
              if (window.__gifMakerDownloadHook) return;
              window.__gifMakerDownloadHook = true;
              const origClick = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function() {
                try {
                  const href = this.href || '';
                  const name = this.download || 'gifmaker.gif';
                  if ((href.startsWith('blob:') || href.startsWith('data:')) && window.$JS_BRIDGE_NAME) {
                    fetch(href).then(function(res) { return res.blob(); }).then(function(blob) {
                      const reader = new FileReader();
                      reader.onloadend = function() {
                        $JS_BRIDGE_NAME.saveFile(reader.result, name, blob.type || 'image/gif');
                      };
                      reader.readAsDataURL(blob);
                    });
                    return;
                  }
                } catch (e) {}
                return origClick.apply(this, arguments);
              };
            })();
        """.trimIndent()
    }
}
