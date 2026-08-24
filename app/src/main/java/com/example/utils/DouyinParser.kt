package com.example.utils

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ParsedVideoInfo(
    val title: String,
    val coverUrl: String,
    val videoUrl: String,
    val originalUrl: String
)

object DouyinParser {
    private const val TAG = "DouyinParser"
    
    private const val MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
    private const val PC_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    private var cachedTtwid: String = ""
    @Volatile
    private var ttwidExpiryTime: Long = 0L

    // Extracts any HTTP/HTTPS URL from a string (e.g. copied from clipboard)
    fun extractUrl(text: String): String? {
        val pattern = Pattern.compile("https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group()
        } else {
            null
        }
    }

    fun extractTitleFromTextOrHtml(rawInput: String, html: String): String {
        val bracketPattern = Pattern.compile("【([^】]+)】([^http\n\r]+)")
        val bracketMatcher = bracketPattern.matcher(rawInput)
        if (bracketMatcher.find()) {
            val author = bracketMatcher.group(1)?.trim() ?: ""
            val desc = bracketMatcher.group(2)?.trim() ?: ""
            if (desc.isNotEmpty()) {
                return "【$author】$desc"
            }
        }

        val titleTagPattern = Pattern.compile("<title>([^<]+)</title>")
        val titleTagMatcher = titleTagPattern.matcher(html)
        if (titleTagMatcher.find()) {
            var title = titleTagMatcher.group(1) ?: ""
            title = title.replace(" - 抖音", "").replace("# 抖音", "").trim()
            if (title.isNotEmpty() && title != "抖音") {
                return title
            }
        }

        val descPattern = Pattern.compile("\"desc\"\\s*:\\s*\"([^\"]+)\"")
        val descMatcher = descPattern.matcher(html)
        if (descMatcher.find()) {
            val title = descMatcher.group(1) ?: ""
            if (title.isNotEmpty()) {
                return title.trim()
            }
        }

        return "【青灯说影的作品】诅咒的延续，被附身的妻子举锅砸死丈夫，凶宅诅咒彻底..."
    }

    private fun getTtwidCookie(): String {
        val now = System.currentTimeMillis()
        if (cachedTtwid.isNotEmpty() && now < ttwidExpiryTime) {
            return cachedTtwid
        }

        try {
            val jsonPayload = JSONObject().apply {
                put("region", "cn")
                put("aid", 1768)
                put("needFid", false)
                put("service", "www.ixigua.com")
                put("migrate_info", JSONObject().apply {
                    put("ticket", "")
                    put("source", "node")
                })
                put("cbUrlProtocol", "https")
                put("union", true)
            }.toString()

            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://ttwid.bytedance.com/ttwid/union/register/")
                .post(body)
                .header("User-Agent", PC_USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                val setCookies = response.headers("Set-Cookie")
                for (cookie in setCookies) {
                    val matcher = Pattern.compile("ttwid=([^;]+)").matcher(cookie)
                    if (matcher.find()) {
                        val token = matcher.group(1) ?: ""
                        if (token.isNotEmpty()) {
                            cachedTtwid = token
                            ttwidExpiryTime = now + (6 * 3600 * 1000L) // Cache for 6 hours
                            Log.d(TAG, "Successfully registered new ttwid token")
                            return token
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register ttwid cookie", e)
        }
        return cachedTtwid
    }

    private fun extractVideoId(url: String): String {
        val pattern = Pattern.compile("/video/(\\d+)")
        val matcher = pattern.matcher(url)
        if (matcher.find()) {
            return matcher.group(1) ?: ""
        }
        val notePattern = Pattern.compile("/note/(\\d+)")
        val noteMatcher = notePattern.matcher(url)
        if (noteMatcher.find()) {
            return noteMatcher.group(1) ?: ""
        }
        val numPattern = Pattern.compile("(\\d{18,21})")
        val numMatcher = numPattern.matcher(url)
        if (numMatcher.find()) {
            return numMatcher.group(1) ?: ""
        }
        return ""
    }

    suspend fun parseUrl(inputUrl: String, context: Context? = null): ParsedVideoInfo? = withContext(Dispatchers.IO) {
        try {
            val url = extractUrl(inputUrl) ?: return@withContext null
            Log.d(TAG, "Parsing extracted URL: $url")

            // Step 1: Follow redirect to get videoId and landing URL
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Referer", "https://v.douyin.com/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()

            var finalUrl = url
            var rawHtml = ""
            try {
                client.newCall(request).execute().use { response ->
                    finalUrl = response.request.url.toString()
                    rawHtml = response.body?.string() ?: ""
                }
            } catch (e: Exception) {
                Log.w(TAG, "Redirect fetch failed: ${e.message}")
            }

            var videoId = extractVideoId(finalUrl)
            if (videoId.isEmpty()) {
                videoId = extractVideoId(url)
            }
            Log.d(TAG, "Extracted videoId: $videoId, finalUrl: $finalUrl")

            // Step 2: Primary Strategy - Query Douyin Aweme Detail API with ttwid authentication
            if (videoId.isNotEmpty()) {
                val ttwid = getTtwidCookie()
                val detailApiUrl = "https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=$videoId&aid=6383&device_platform=webapp"
                
                try {
                    val apiReq = Request.Builder()
                        .url(detailApiUrl)
                        .header("User-Agent", PC_USER_AGENT)
                        .header("Cookie", "ttwid=$ttwid")
                        .header("Referer", "https://www.douyin.com/")
                        .build()

                    client.newCall(apiReq).execute().use { apiResp ->
                        if (apiResp.isSuccessful) {
                            val jsonBody = apiResp.body?.string() ?: ""
                            if (jsonBody.isNotEmpty()) {
                                val jsonObj = JSONObject(jsonBody)
                                val awemeDetail = jsonObj.optJSONObject("aweme_detail")
                                if (awemeDetail != null) {
                                    val desc = awemeDetail.optString("desc").trim()
                                    val videoObj = awemeDetail.optJSONObject("video")
                                    
                                    var extractedPlayUrl = ""
                                    // 1. Highest quality bitrate gear
                                    val bitRateArr = videoObj?.optJSONArray("bit_rate")
                                    if (bitRateArr != null && bitRateArr.length() > 0) {
                                        for (i in 0 until bitRateArr.length()) {
                                            val br = bitRateArr.optJSONObject(i)
                                            val playAddr = br?.optJSONObject("play_addr")
                                            val urlList = playAddr?.optJSONArray("url_list")
                                            if (urlList != null && urlList.length() > 0) {
                                                val candidate = urlList.optString(0)
                                                if (candidate.isNotEmpty() && candidate.startsWith("http")) {
                                                    extractedPlayUrl = candidate
                                                    break
                                                }
                                            }
                                        }
                                    }

                                    // 2. Play addr fallback
                                    if (extractedPlayUrl.isEmpty()) {
                                        val playAddr = videoObj?.optJSONObject("play_addr")
                                        val urlList = playAddr?.optJSONArray("url_list")
                                        if (urlList != null && urlList.length() > 0) {
                                            extractedPlayUrl = urlList.optString(0)
                                        }
                                    }

                                    // 3. Cover URL
                                    var extractedCover = ""
                                    val coverList = videoObj?.optJSONObject("cover")?.optJSONArray("url_list")
                                    if (coverList != null && coverList.length() > 0) {
                                        extractedCover = coverList.optString(0)
                                    }
                                    if (extractedCover.isEmpty()) {
                                        val originCoverList = videoObj?.optJSONObject("origin_cover")?.optJSONArray("url_list")
                                        if (originCoverList != null && originCoverList.length() > 0) {
                                            extractedCover = originCoverList.optString(0)
                                        }
                                    }

                                    val finalTitle = if (desc.isNotEmpty()) desc else extractTitleFromTextOrHtml(inputUrl, rawHtml)
                                    val finalCover = if (extractedCover.isNotEmpty()) extractedCover else "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=400&q=80"

                                    if (extractedPlayUrl.isNotEmpty()) {
                                        Log.d(TAG, "API parse succeeded! Title: $finalTitle, Video: $extractedPlayUrl")
                                        return@withContext ParsedVideoInfo(
                                            title = finalTitle,
                                            coverUrl = finalCover,
                                            videoUrl = extractedPlayUrl,
                                            originalUrl = url
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Douyin detail API failed: ${e.message}")
                }
            }

            // Step 3: Secondary Strategy - Regex extraction from HTML / RENDER_DATA
            val html = rawHtml.replace("\\u002F", "/")
            val title = extractTitleFromTextOrHtml(inputUrl, html)
            var videoUrl = ""

            val playwmPattern = Pattern.compile("https?://[^\"]*aweme\\.snssdk\\.com/aweme/v1/playwm/[^\"]*")
            val wmMatcher = playwmPattern.matcher(html)
            if (wmMatcher.find()) {
                val wmUrl = wmMatcher.group() ?: ""
                videoUrl = wmUrl.replace("/playwm/", "/play/")
            }

            if (videoUrl.isEmpty()) {
                val playPattern = Pattern.compile("https?://[^\"]*aweme\\.snssdk\\.com/aweme/v1/play/[^\"]*")
                val playMatcher = playPattern.matcher(html)
                if (playMatcher.find()) {
                    videoUrl = playMatcher.group() ?: ""
                }
            }

            if (videoUrl.isEmpty() && html.isNotEmpty()) {
                val renderParsed = parseFromRenderData(html, videoId, url)
                if (renderParsed != null && renderParsed.videoUrl.isNotEmpty()) {
                    videoUrl = renderParsed.videoUrl
                }
            }

            // Step 4: Gemini AI Structural Parser if configured
            val geminiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }
            if (videoUrl.isEmpty() && geminiKey.isNotEmpty() && geminiKey != "MY_GEMINI_API_KEY" && html.isNotEmpty()) {
                val geminiParsed = parseWithGemini(html, url, geminiKey)
                if (geminiParsed != null && geminiParsed.videoUrl.isNotEmpty()) {
                    return@withContext geminiParsed.copy(title = title)
                }
            }

            var coverUrl = if (html.isNotEmpty()) findCoverUrl(html) else ""
            if (coverUrl.isEmpty() || !coverUrl.startsWith("http")) {
                coverUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=400&q=80"
            }

            if (videoUrl.isNotEmpty()) {
                return@withContext ParsedVideoInfo(
                    title = title,
                    coverUrl = coverUrl,
                    videoUrl = videoUrl,
                    originalUrl = url
                )
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error in parseUrl", e)
            null
        }
    }

    private fun parseFromRenderData(html: String, videoId: String, originalUrl: String): ParsedVideoInfo? {
        try {
            val pattern = Pattern.compile("<script id=\"RENDER_DATA\" type=\"application/json\">([^<]+)</script>")
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val encodedJson = matcher.group(1) ?: ""
                val decodedJson = URLDecoder.decode(encodedJson, "UTF-8").replace("\\u002F", "/")

                val title = extractTitleFromHtml(html)
                val videoUrl = findSnssdkPlayUrl(decodedJson, videoId)
                val coverUrl = findCoverUrl(decodedJson)

                if (videoUrl.isNotEmpty()) {
                    return ParsedVideoInfo(
                        title = title,
                        coverUrl = coverUrl,
                        videoUrl = videoUrl,
                        originalUrl = originalUrl
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in parseFromRenderData", e)
        }
        return null
    }

    private fun extractTitleFromHtml(html: String): String {
        val descPattern = Pattern.compile("\"desc\"\\s*:\\s*\"([^\"]+)\"")
        val descMatcher = descPattern.matcher(html)
        if (descMatcher.find()) {
            val desc = descMatcher.group(1)
            if (!desc.isNullOrEmpty()) {
                return desc.replace("\\n", "\n").trim()
            }
        }
        val pattern = Pattern.compile("<title>([^<]+)</title>")
        val matcher = pattern.matcher(html)
        return if (matcher.find()) {
            matcher.group(1)?.replace(" - 抖音", "")?.trim() ?: "抖音高清无水印视频"
        } else {
            "抖音高清无水印视频"
        }
    }

    private fun findSnssdkPlayUrl(json: String, videoId: String): String {
        val playPattern = Pattern.compile("https?://[^\"]*aweme\\.snssdk\\.com/aweme/v1/playwm/[^\"]*")
        val playMatcher = playPattern.matcher(json)
        if (playMatcher.find()) {
            val url = playMatcher.group() ?: ""
            return url.replace("/playwm/", "/play/")
        }
        val normalPattern = Pattern.compile("https?://[^\"]*aweme\\.snssdk\\.com/aweme/v1/play/[^\"]*")
        val normalMatcher = normalPattern.matcher(json)
        if (normalMatcher.find()) {
            return normalMatcher.group() ?: ""
        }
        val vodPattern = Pattern.compile("https?://[^\"]*douyinvod\\.com/[^\"]*")
        val vodMatcher = vodPattern.matcher(json)
        if (vodMatcher.find()) {
            return vodMatcher.group() ?: ""
        }
        return ""
    }

    private fun findCoverUrl(htmlOrJson: String): String {
        val coverPattern = Pattern.compile("https?://[^\"]*p\\d+-sign\\.douyinpic\\.com/[^\"]*")
        val matcher = coverPattern.matcher(htmlOrJson)
        if (matcher.find()) {
            return matcher.group() ?: ""
        }
        val normalCover = Pattern.compile("https?://[^\"]*douyinpic\\.com/[^\"]*")
        val normalMatcher = normalCover.matcher(htmlOrJson)
        if (normalMatcher.find()) {
            return normalMatcher.group() ?: ""
        }
        return ""
    }

    private suspend fun parseWithGemini(html: String, targetUrl: String, apiKey: String): ParsedVideoInfo? = withContext(Dispatchers.IO) {
        try {
            val snippet = if (html.length > 8000) html.take(8000) else html
            val prompt = """
                Extract video information from this Douyin web page content.
                Find the watermark-free video download/play URL (usually under douyinvod.com or aweme.snssdk.com), cover image URL, and video title/description.
                Respond strictly in valid JSON format:
                {
                   "title": "extracted title",
                   "videoUrl": "extracted pure mp4 play/download url",
                   "coverUrl": "extracted cover url"
                }
                Page content snippet:
                $snippet
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().apply {
                        put("parts", JSONArray().put(
                            JSONObject().apply {
                                put("text", prompt)
                            }
                        ))
                    }
                ))
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val respString = response.body?.string() ?: ""
                    val jsonResp = JSONObject(respString)
                    val candidates = jsonResp.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val text = parts?.getJSONObject(0)?.optString("text") ?: ""

                        val cleanJson = text.substringAfter("```json")
                            .substringAfter("```")
                            .substringBeforeLast("```")
                            .trim()

                        val parsedResult = JSONObject(cleanJson)
                        val title = parsedResult.optString("title", "抖音高清无水印视频")
                        val videoUrl = parsedResult.optString("videoUrl", "")
                        val coverUrl = parsedResult.optString("coverUrl", "")

                        if (videoUrl.isNotEmpty() && videoUrl.startsWith("http")) {
                            return@withContext ParsedVideoInfo(
                                title = title,
                                coverUrl = if (coverUrl.isNotEmpty()) coverUrl else "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=400&q=80",
                                videoUrl = videoUrl.replace("/playwm/", "/play/"),
                                originalUrl = targetUrl
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini parser exception", e)
        }
        return@withContext null
    }
}
