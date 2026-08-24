package com.example.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object DouyinWebViewParser {
    private const val TAG = "DouyinWebViewParser"
    private const val USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun parseWithWebView(context: Context, rawInput: String, targetUrl: String, timeoutMs: Long = 12000L): ParsedVideoInfo? {
        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main) {
                suspendCoroutine { continuation ->
                    val isCompleted = AtomicBoolean(false)
                    var webView: WebView? = null
                    val handler = Handler(Looper.getMainLooper())

                    fun finishWithResult(result: ParsedVideoInfo?) {
                        if (isCompleted.compareAndSet(false, true)) {
                            handler.removeCallbacksAndMessages(null)
                            try {
                                webView?.stopLoading()
                                webView?.loadUrl("about:blank")
                                webView?.destroy()
                            } catch (e: Exception) {
                                Log.w(TAG, "Error cleaning up WebView", e)
                            }
                            continuation.resume(result)
                        }
                    }

                    try {
                        webView = WebView(context.applicationContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.userAgentString = USER_AGENT
                            settings.cacheMode = WebSettings.LOAD_DEFAULT

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: ""
                                    
                                    // Intercept media video URLs served by Douyin / ByteDance CDN
                                    if (reqUrl.contains(".douyinvod.com") ||
                                        reqUrl.contains("aweme.snssdk.com/aweme/v1/play") ||
                                        reqUrl.contains("tos-cn-v") ||
                                        reqUrl.contains("v26-web.douyinvod.com") ||
                                        reqUrl.contains("v3-web.douyinvod.com") ||
                                        reqUrl.contains("v9-web.douyinvod.com")
                                    ) {
                                        Log.d(TAG, "Intercepted video media stream: $reqUrl")
                                        val pureVideoUrl = reqUrl.replace("/playwm/", "/play/")
                                        
                                        // Retrieve page title and cover asynchronously on UI thread
                                        handler.post {
                                            view?.evaluateJavascript(
                                                """
                                                (function() {
                                                    var v = document.querySelector('video');
                                                    var poster = v ? (v.poster || '') : '';
                                                    var desc = document.querySelector('.desc, [data-e2e="video-desc"], .title')?.innerText || document.title || '';
                                                    return JSON.stringify({ poster: poster, desc: desc });
                                                })();
                                                """.trimIndent()
                                            ) { jsonStr ->
                                                var extractedTitle = DouyinParser.extractTitleFromTextOrHtml(rawInput, "")
                                                var cover = "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=400&q=80"
                                                try {
                                                    val cleanJson = jsonStr?.trim('"', '\\', ' ')?.replace("\\\"", "\"") ?: ""
                                                    if (cleanJson.startsWith("{")) {
                                                        val obj = JSONObject(cleanJson)
                                                        val desc = obj.optString("desc")
                                                        if (desc.isNotEmpty() && !desc.contains("抖音")) {
                                                            extractedTitle = desc
                                                        }
                                                        val poster = obj.optString("poster")
                                                        if (poster.startsWith("http")) {
                                                            cover = poster
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.w(TAG, "Error parsing JS evaluation", e)
                                                }

                                                finishWithResult(
                                                    ParsedVideoInfo(
                                                        title = extractedTitle,
                                                        coverUrl = cover,
                                                        videoUrl = pureVideoUrl,
                                                        originalUrl = targetUrl
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    Log.d(TAG, "WebView page finished: $url")

                                    // Polling script to find video tag src after hydration
                                    var pollCount = 0
                                    val pollRunnable = object : Runnable {
                                        override fun run() {
                                            if (isCompleted.get()) return
                                            pollCount++
                                            view?.evaluateJavascript(
                                                """
                                                (function() {
                                                    var v = document.querySelector('video');
                                                    var src = v ? (v.currentSrc || v.src || '') : '';
                                                    var poster = v ? (v.poster || '') : '';
                                                    var desc = document.querySelector('.desc, [data-e2e="video-desc"], .title')?.innerText || document.title || '';
                                                    return JSON.stringify({ src: src, poster: poster, desc: desc });
                                                })();
                                                """.trimIndent()
                                            ) { result ->
                                                try {
                                                    val clean = result?.trim('"', '\\', ' ')?.replace("\\\"", "\"") ?: ""
                                                    if (clean.startsWith("{")) {
                                                        val obj = JSONObject(clean)
                                                        val src = obj.optString("src")
                                                        if (src.isNotEmpty() && src.startsWith("http")) {
                                                            val pureUrl = src.replace("/playwm/", "/play/")
                                                            val poster = obj.optString("poster")
                                                            val desc = obj.optString("desc")
                                                            val title = if (desc.isNotEmpty() && !desc.contains("抖音")) desc else DouyinParser.extractTitleFromTextOrHtml(rawInput, "")
                                                            val cover = if (poster.startsWith("http")) poster else "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=400&q=80"
                                                            
                                                            finishWithResult(
                                                                ParsedVideoInfo(
                                                                    title = title,
                                                                    coverUrl = cover,
                                                                    videoUrl = pureUrl,
                                                                    originalUrl = targetUrl
                                                                )
                                                            )
                                                            return@evaluateJavascript
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.w(TAG, "DOM poll error", e)
                                                }

                                                if (pollCount < 10 && !isCompleted.get()) {
                                                    handler.postDelayed(this, 800)
                                                }
                                            }
                                        }
                                    }
                                    handler.postDelayed(pollRunnable, 1000)
                                }
                            }
                        }

                        webView?.loadUrl(targetUrl)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed initializing parser WebView", e)
                        finishWithResult(null)
                    }
                }
            }
        }
    }
}
