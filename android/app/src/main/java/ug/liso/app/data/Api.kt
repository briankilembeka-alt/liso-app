package ug.liso.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

@Serializable data class SignInResult(val token: String, val userId: String, val isNewUser: Boolean)
@Serializable data class CallCreated(val id: String, val status: String)
@Serializable data class CallState(val id: String, val sellerId: String, val status: String, val role: String)
@Serializable data class PublishLimits(val width: Int, val height: Int, val maxBitrateKbps: Int)
@Serializable data class JoinToken(val token: String, val expiresAt: String, val publish: PublishLimits)
@Serializable data class JoinResult(val join: JoinToken)

@Serializable private data class CategoriesResponse(val categories: List<Category>)
@Serializable private data class SellersResponse(val sellers: List<Seller>)

/** The server said no, with a reason code such as ID_VERIFICATION_REQUIRED or BAD_CODE. */
class ApiException(val status: Int, val code: String, message: String) : IOException(message)

/** Thin client for the Liso API. Small payloads and short timeouts suit patchy 3G. */
class Api(
    private val session: Session? = null,
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    private suspend fun send(path: String, body: Map<String, String>? = null): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl + path)
        session?.token?.let { req.header("Authorization", "Bearer $it") }
        if (body != null) req.post(JsonObject(body.mapValues { JsonPrimitive(it.value) }).toString().toRequestBody(jsonType))
        http.newCall(req.build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                if (res.code == 401) session?.token = null
                val err = runCatching { json.parseToJsonElement(text).jsonObject["error"] as JsonObject }.getOrNull()
                throw ApiException(
                    res.code,
                    err?.get("code")?.jsonPrimitive?.content ?: "HTTP_${res.code}",
                    err?.get("message")?.jsonPrimitive?.content ?: "request failed",
                )
            }
            text
        }
    }

    suspend fun categories(lang: String): List<Category> =
        json.decodeFromString<CategoriesResponse>(send("/categories?lang=$lang")).categories

    suspend fun sellers(category: String): List<Seller> =
        json.decodeFromString<SellersResponse>(send("/sellers?category=$category")).sellers

    suspend fun topSellers(category: String): List<Seller> =
        json.decodeFromString<SellersResponse>(send("/sellers/top?category=$category")).sellers

    suspend fun seller(id: String): Seller = json.decodeFromString(send("/sellers/$id"))

    suspend fun requestCode(phone: String) { send("/auth/otp", mapOf("phone" to phone)) }

    suspend fun verifyCode(phone: String, code: String): SignInResult =
        json.decodeFromString<SignInResult>(send("/auth/verify", mapOf("phone" to phone, "code" to code)))
            .also { session?.token = it.token }

    suspend fun verifyNationalId(nin: String) { send("/me/id-verification", mapOf("nationalIdNumber" to nin)) }

    suspend fun requestCall(sellerId: String): CallCreated =
        json.decodeFromString(send("/calls", mapOf("sellerId" to sellerId)))

    suspend fun call(id: String): CallState = json.decodeFromString(send("/calls/$id"))

    suspend fun joinCall(id: String): JoinToken = json.decodeFromString<JoinResult>(send("/calls/$id/join", emptyMap())).join

    suspend fun endCall(id: String) { send("/calls/$id/end", emptyMap()) }
}
