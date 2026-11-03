package com.gangas.core.net

import com.gangas.core.model.Condition
import com.gangas.core.model.Listing
import com.gangas.core.model.Site
import com.gangas.core.model.Source
import com.gangas.core.net.Http.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Credenciales de una app creada en developers.mercadolibre.com (grant client_credentials). */
data class MeliCredentials(
    val clientId: String = "",
    val clientSecret: String = "",
    /** Token pegado manualmente (opcional). Si existe, se usa tal cual. */
    val manualToken: String = "",
) {
    val isConfigured get() = manualToken.isNotBlank() || (clientId.isNotBlank() && clientSecret.isNotBlank())
}

/** Cliente de la API oficial: https://api.mercadolibre.com/sites/{SITE}/search */
class MeliApiClient(
    private val client: OkHttpClient,
    private val credentials: () -> MeliCredentials,
    private val baseUrl: String = "https://api.mercadolibre.com",
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiry: Long = 0

    @Serializable private data class TokenResponse(@SerialName("access_token") val accessToken: String, @SerialName("expires_in") val expiresIn: Long = 21600)
    @Serializable private data class Shipping(@SerialName("free_shipping") val freeShipping: Boolean = false)
    @Serializable private data class Seller(val id: Long? = null)
    @Serializable private data class ApiItem(
        val id: String,
        val title: String = "",
        val price: Double? = null,
        @SerialName("original_price") val originalPrice: Double? = null,
        @SerialName("currency_id") val currencyId: String = "",
        val condition: String? = null,
        val permalink: String = "",
        val thumbnail: String? = null,
        val shipping: Shipping? = null,
        val seller: Seller? = null,
        @SerialName("official_store_id") val officialStoreId: Long? = null,
    )
    @Serializable private data class Paging(val total: Int = 0)
    @Serializable private data class SearchResponse(val results: List<ApiItem> = emptyList(), val paging: Paging = Paging())

    private suspend fun token(): String? {
        val c = credentials()
        if (c.manualToken.isNotBlank()) return c.manualToken.trim()
        if (c.clientId.isBlank() || c.clientSecret.isBlank()) return null
        cachedToken?.let { if (System.currentTimeMillis() < tokenExpiry) return it }
        val req = Request.Builder().url("$baseUrl/oauth/token")
            .post(FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", c.clientId.trim())
                .add("client_secret", c.clientSecret.trim())
                .build())
            .header("Accept", "application/json")
            .build()
        client.newCall(req).await().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw HttpException(resp.code, "No se pudo obtener token (${resp.code})")
            val t = json.decodeFromString<TokenResponse>(body)
            cachedToken = t.accessToken
            tokenExpiry = System.currentTimeMillis() + (t.expiresIn - 300).coerceAtLeast(60) * 1000
            return t.accessToken
        }
    }

    fun invalidateToken() { cachedToken = null; tokenExpiry = 0 }

    suspend fun search(site: Site, query: String, offset: Int, limit: Int = 50, sortPriceAsc: Boolean): List<Listing> {
        val url = "$baseUrl/sites/${site.id}/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
            .apply { if (sortPriceAsc) addQueryParameter("sort", "price_asc") }
            .build()
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        token()?.let { builder.header("Authorization", "Bearer $it") }
        val body = try {
            Http.getText(client, builder.build())
        } catch (e: HttpException) {
            if (e.code == 401) invalidateToken()
            throw e
        }
        return json.decodeFromString<SearchResponse>(body).results.mapNotNull { it.toListing() }
    }

    private fun ApiItem.toListing(): Listing? {
        val p = price ?: return null
        return Listing(
            id = id,
            title = title,
            price = p,
            originalPrice = originalPrice,
            currency = currencyId,
            condition = when (condition) { "new" -> Condition.NEW; "used" -> Condition.USED; else -> Condition.UNKNOWN },
            permalink = permalink,
            thumbnail = thumbnail?.replace("http://", "https://"),
            freeShipping = shipping?.freeShipping == true,
            sellerId = seller?.id,
            officialStore = officialStoreId != null,
            source = Source.API,
        )
    }
}
