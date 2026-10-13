package com.mytivi.app

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object Http {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Mở luồng dữ liệu từ URL, tự giải nén nếu file là .gz (nhiều nguồn EPG nén gzip). */
    fun open(url: String): InputStream {
        val request = Request.Builder().url(url).header("User-Agent", "MyTivi/1.0").build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IllegalStateException("HTTP $code")
        }
        val stream = BufferedInputStream(response.body!!.byteStream())
        stream.mark(2)
        val b1 = stream.read()
        val b2 = stream.read()
        stream.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(stream) else stream
    }

    fun getString(url: String): String = open(url).bufferedReader().use { it.readText() }
}
