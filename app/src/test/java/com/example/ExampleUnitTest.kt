package com.example

import com.example.utils.DouyinParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {
  @Test
  fun testDouyinParser() = runBlocking {
    val url = "https://v.douyin.com/IBvolbUrtFw/"
    println("--- START TEST NEW PARSER ---")
    
    val result = DouyinParser.parseUrl(url)
    assertNotNull("Parsed result should not be null!", result)
    
    println("Successfully parsed!")
    println("Title: ${result?.title}")
    println("CoverUrl: ${result?.coverUrl}")
    println("VideoUrl: ${result?.videoUrl}")
    
    assertTrue("Should be a snssdk play URL or valid stream link!", result?.videoUrl?.contains("aweme.snssdk.com/aweme/v1/play") == true)
    assertFalse("Should be watermark-free!", result?.videoUrl?.contains("/playwm/") == true)
    
    println("--- END TEST NEW PARSER ---")
  }
}
