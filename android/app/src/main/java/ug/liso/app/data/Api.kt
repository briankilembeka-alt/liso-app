package ug.liso.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ug.liso.app.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class Category(val id: String, val parentId: String? = null, val depth: Int, val name: String, val rank: Int)

@Serializable
data class Seller(
    val id: String,
    val businessName: String,
    val kind: String,
    val district: String,
    val trustScore: Double,
    val categories: List<String> = emptyList(),
    val canTakeCalls: Boolean = true,
    val sponsored: Boolean = false,
    val distanceKm: Double? = null,
    val completedDeals: Int? = null,
)

@Serializable private data class CategoriesResponse(val categories: List<Category>)
@Serializable private data class SellersResponse(val sellers: List<Seller>)

/** Thin client for the Liso API. Small payloads and short timeouts suit patchy 3G. */
class Api(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(baseUrl + path).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code} for $path")
            res.body?.string() ?: throw IOException("empty body for $path")
        }
    }

    suspend fun categories(lang: String): List<Category> =
        json.decodeFromString<CategoriesResponse>(get("/categories?lang=$lang")).categories

    suspend fun sellers(category: String): List<Seller> =
        json.decodeFromString<SellersResponse>(get("/sellers?category=$category")).sellers

    suspend fun topSellers(category: String): List<Seller> =
        json.decodeFromString<SellersResponse>(get("/sellers/top?category=$category")).sellers

    suspend fun seller(id: String): Seller = json.decodeFromString(get("/sellers/$id"))
}
