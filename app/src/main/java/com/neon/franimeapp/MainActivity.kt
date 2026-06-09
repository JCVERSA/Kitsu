package com.neon.franimeapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

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
            settings.setAllowFileAccess(true)
            settings.setAllowContentAccess(true)

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
                    view?.evaluateJavascript(VIDEO_EXTRACTION_SCRIPT) { result ->
                        Log.d("FranimeApp", "JavaScript evaluation result: $result")
                    }
                }
            }

            // Set WebChromeClient for JavaScript dialogs, etc.
            webChromeClient = object : WebChromeClient() {
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
        }
    }

    /**
     * JavaScript injection payload to extract video URLs from Franime site.
     */
    private val VIDEO_EXTRACTION_SCRIPT = """
        (function() {
            'use strict';

            // Configuration
            const CONFIG = {
                // Timeout for waiting for video elements (ms)
                ELEMENT_TIMEOUT: 15000,

                // Polling interval for dynamic content (ms)
                POLL_INTERVAL: 500,

                // Known video player selectors (in order of likelihood)
                VIDEO_SELECTORS: [
                    'video[src*=".m3u8"]',          // Direct HLS in video tag
                    'video[src*=".mp4"]',           // Direct MP4
                    '.video-js video',              // Video.js player
                    '.jwplayer video',              // JW Player
                    '#player video',                // Generic player container
                    '[class*="player"] video',      // Class-based player
                    'iframe[src*="player"]',        // Player iframes
                    'iframe[src*="embed"]'          // Embed iframes
                ],

                // Known player library indicators
                PLAYER_INDICATORS: [
                    'hls.js',
                    'video.js',
                    'jwplayer',
                    'Dash.js',
                    'shaka-player',
                    'mediaelement',
                    'plyr'
                ]
            };

            /**
             * Sends found URL to Android layer
             * @param {string} url - The video URL to send
             */
            function sendUrlToAndroid(url) {
                if (window.Android && typeof window.Android.onVideoUrlFound === 'function') {
                    try {
                        window.Android.onVideoUrlFound(url);
                        console.log('[FranimeExtractor] Sent URL to Android:', url);
                        return true;
                    } catch (e) {
                        console.error('[FranimeExtractor] Failed to send URL to Android:', e);
                    }
                } else {
                    console.warn('[FranimeExtractor] Android interface not available');
                }
                return false;
            }

            /**
             * Converts relative URL to absolute using current document base
             * @param {string} url - URL to convert
             * @returns {string} Absolute URL
             */
            function resolveUrl(url) {
                try {
                    return new URL(url, window.location.href).href;
                } catch (e) {
                    console.warn('[FranimeExtractor] Invalid URL:', url, e);
                    return url;
                }
            }

            /**
             * Extracts video URL from various sources
             * @returns {string|null} Video URL or null if not found
             */
            function extractVideoUrl() {
                // 1. Check direct video elements
                const videoElements = document.querySelectorAll('video');
                for (const video of videoElements) {
                    if (video.src && video.src.includes('.m3u8')) {
                        return resolveUrl(video.src);
                    }
                    if (video.currentSrc && video.currentSrc.includes('.m3u8')) {
                        return resolveUrl(video.currentSrc);
                    }

                    // Check srcset for adaptive sources
                    if (video.srcset) {
                        const sources = video.srcset.split(',').map(s => s.trim().split(' ')[0]);
                        for (const source of sources) {
                            if (source.includes('.m3u8')) {
                                return resolveUrl(source);
                            }
                        }
                    }
                }

                // 2. Check source elements inside video tags
                const sourceElements = document.querySelectorAll('video source');
                for (const source of sourceElements) {
                    if (source.src && (source.src.includes('.m3u8') || source.src.includes('.mp4'))) {
                        return resolveUrl(source.src);
                    }
                }

                // 3. Check for blob URLs (less useful for extraction but indicates streaming)
                const blobVideos = document.querySelectorAll('video[src^="blob:"]');
                if (blobVideos.length > 0) {
                    console.log('[FranimeExtractor] Found blob URL - attempting to intercept network requests');
                    // Blob URLs require network interception approach (handled below)
                }

                // 4. Check iframes for embedded players
                const iframes = document.querySelectorAll('iframe');
                for (const iframe of iframes) {
                    // Skip hidden/ad iframes
                    if (iframe.offsetParent === null ||
                        iframe.width < 100 ||
                        iframe.height < 100) {
                        continue;
                    }

                    // Try to access iframe content (may fail due to same-origin policy)
                    try {
                        if (iframe.contentDocument && iframe.contentDocument.querySelector) {
                            const iframeVideo = iframe.contentDocument.querySelector('video');
                            if (iframeVideo && (iframeVideo.src || iframeVideo.currentSrc)) {
                                const url = iframeVideo.src || iframeVideo.currentSrc;
                                if (url.includes('.m3u8') || url.includes('.mp4')) {
                                    return resolveUrl(url);
                                }
                            }
                        }
                    } catch (e) {
                        // Cross-origin iframe - can't access content directly
                        console.log('[FranimeExtractor] Cross-origin iframe detected:', iframe.src);
                        // We'll rely on network interception for these
                    }
                }

                return null;
            }

            /**
             * Hooks into network requests to catch manifest/video URLs
             * @returns {Function} Cleanup function to remove hooks
             */
            function hookNetworkRequests() {
                const originalFetch = window.fetch;
                const originalXHR = window.XMLHttpRequest;

                // Hook fetch API
                window.fetch = function(resource, init) {
                    return Promise.resolve().then(() => {
                        return originalFetch.call(this, resource, init).then(response => {
                            // Check if this looks like a manifest request
                            const url = typeof resource === 'string' ? resource : resource.url;
                            if (url && (url.includes('.m3u8') || url.includes('.mpd'))) {
                                console.log('[FranimeExtractor] Intercepted manifest request:', url);
                                const absoluteUrl = resolveUrl(url);
                                sendUrlToAndroid(absoluteUrl);
                            }
                            return response;
                        });
                    });
                };

                // Hook XMLHttpRequest
                window.XMLHttpRequest = function() {
                    const xhr = new originalXHR();
                    const originalOpen = xhr.open;
                    xhr.open = function(method, url) {
                        this._url = url;
                        return originalOpen.apply(this, arguments);
                    };
                    const originalSend = xhr.send;
                    xhr.send = function(body) {
                        if (this._url && (this._url.includes('.m3u8') || this._url.includes('.mpd'))) {
                            console.log('[FranimeExtractor] Intercepted XHR request:', this._url);
                            const absoluteUrl = resolveUrl(this._url);
                            sendUrlToAndroid(absoluteUrl);
                        }
                        return originalSend.call(this, body);
                    };
                    return xhr;
                };

                // Return cleanup function
                return function() {
                    window.fetch = originalFetch;
                    window.XMLHttpRequest = originalXHR;
                };
            }

            /**
             * Main initialization function
             */
            function initializeExtractor() {
                console.log('[FranimeExtractor] Initializing video URL extractor');

                // Try immediate extraction
                let videoUrl = extractVideoUrl();
                if (videoUrl) {
                    sendUrlToAndroid(videoUrl);
                    return;
                }

                // Set up network hooks for dynamic content
                const cleanup = hookNetworkRequests();

                // Set up MutationObserver for dynamically added video elements
                const observer = new MutationObserver((mutations) => {
                    for (const mutation of mutations) {
                        for (const node of mutation.addedNodes) {
                            if (node.nodeType === 1) { // Element node
                                // Check if this is or contains a video element
                                if (node.matches && node.matches('video')) {
                                    const url = extractVideoUrl();
                                    if (url) {
                                        sendUrlToAndroid(url);
                                        observer.disconnect();
                                        cleanup();
                                        return;
                                    }
                                }
                                // Check descendants
                                const videos = node.querySelectorAll ? node.querySelectorAll('video') : [];
                                for (const video of videos) {
                                    const url = extractVideoUrl();
                                    if (url) {
                                        sendUrlToAndroid(url);
                                        observer.disconnect();
                                        cleanup();
                                        return;
                                    }
                                }
                            }
                        }
                    }
                });

                // Start observing document body with childList and subtree
                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });

                // Fallback polling for sites that don't trigger mutations reliably
                let pollingAttempts = 0;
                const maxPollingAttempts = CONFIG.ELEMENT_TIMEOUT / CONFIG.POLL_INTERVAL;

                const pollInterval = setInterval(() => {
                    pollingAttempts++;

                    // Try to extract video URL
                    videoUrl = extractVideoUrl();
                    if (videoUrl) {
                        sendUrlToAndroid(videoUrl);
                        clearInterval(pollInterval);
                        observer.disconnect();
                        cleanup();
                        return;
                    }

                    // Stop polling after timeout
                    if (pollingAttempts >= maxPollingAttempts) {
                        console.log('[FranimeExtractor] Polling timeout reached');
                        clearInterval(pollInterval);
                        observer.disconnect();
                        cleanup();
                    }
                }, CONFIG.POLL_INTERVAL);

                // Also check for common player initialization patterns
                document.addEventListener('hlsjsloaded', () => {
                    console.log('[FranimeExtractor] HLS.js detected');
                });

                document.addEventListener('videojsready', () => {
                    console.log('[FranimeExtractor] Video.js detected');
                });
            }

            // Initialize when DOM is ready
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', initializeExtractor);
            } else {
                // DOM already ready
                setTimeout(initializeExtractor, 0);
            }

            // Expose cleanup function for testing (optional)
            window.__FRANIME_EXTRACTOR_CLEANUP = function() {
                console.log('[FranimeExtractor] Cleanup called');
            };
        })();
    """.trimIndent().trimIndent()
}