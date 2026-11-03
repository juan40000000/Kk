package com.gangas.core.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class HttpException(val code: Int, message: String) : IOException(message)

object Http {
    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    private val USER_AGENTS = listOf(
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15",
    )

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    fun randomUserAgent(): String = USER_AGENTS[Random.nextInt(USER_AGENTS.size)]

    suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }

    /**
     * GET con reintentos y backoff exponencial con jitter. Reintenta en errores de red,
     * 429 y 5xx. Devuelve el cuerpo como texto.
     */
    suspend fun getText(client: OkHttpClient, request: Request, maxAttempts: Int = 3): String {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < maxAttempts) {
            try {
                client.newCall(request).await().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) return body
                    val retryable = resp.code == 429 || resp.code >= 500
                    val err = HttpException(resp.code, "HTTP ${resp.code} en ${request.url.host}")
                    if (!retryable) throw err
                    lastError = err
                }
            } catch (e: HttpException) {
                if (e.code != 429 && e.code < 500) throw e
                lastError = e
            } catch (e: IOException) {
                lastError = e
            }
            attempt++
            if (attempt < maxAttempts) delay((400L shl attempt) + Random.nextLong(0, 300))
        }
        throw lastError ?: IOException("Error de red")
    }
}
