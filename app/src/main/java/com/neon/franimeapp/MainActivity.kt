package com.neon.franimeapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

/**
 * MainActivity that hosts a WebView to load Franime site and extract video URLs
 * via JavaScript injection.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create a simple layout with WebView
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Configure WebView
        webView = WebView(this@MainActivity).apply {
            // Configure WebView settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setAllowFileAccess(false)
            settings.setAllowContentAccess(false)

            // Set User-Agent to mimic modern mobile Chrome
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

            // Add JavaScript interface
            addJavascriptInterface(FranimeWebInterface(this@MainActivity), "Android")

            // Set WebViewClient
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return false // Let WebView handle the URL
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Inject JavaScript to extract video URL
                    val script = loadScriptFromAssets("extractor.js")
                    if (script != null) {
                        view?.evaluateJavascript(script) { result ->
                            Log.d("FranimeApp", "JavaScript evaluation result: $result")
                        }
                    } else {
                        Log.e("FranimeApp", "Failed to load extraction script from assets")
                    }
                }
            }

            // Set WebChromeClient for JavaScript dialogs, etc.
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d("FranimeApp-JS", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                    return true
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: android.webkit.JsResult?
                ): Boolean {
                    return super.onJsAlert(view, url, message, result)
                }
            }

            // Load the Franime site
            loadUrl("https://franime.fr")
        }

        // Add WebView to layout
        layout.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        // Set layout as content view
        setContentView(layout)
    }

    /**
     * JavaScript interface to receive video URLs from the web page.
     */
    private class FranimeWebInterface(
        private val context: MainActivity
    ) {
        @JavascriptInterface
        fun onVideoUrlFound(url: String) {
            Log.d("FranimeApp", "Received video URL from JavaScript: $url")
            // Here you could handle the URL - for example, start a video player activity
            context.runOnUiThread {
                Toast.makeText(context, "Video URL Found!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Helper to load JavaScript from assets
     */
    private fun loadScriptFromAssets(fileName: String): String? {
        return try {
            assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e("FranimeApp", "Error reading asset $fileName", e)
            null
        }
    }
}