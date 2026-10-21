/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Exercises Kotlin model binding behind a Retrofit HTTP client: `KotlinJsonAdapterFactory`
 * adapters plugged into `MoshiConverterFactory` decode suspend-function responses, including
 * generic envelope classes, and encode Kotlin request bodies.
 *
 * A warehouse dashboard talks to an inventory REST service: it looks products up, pages
 * through search results, restocks items, and reads the service's structured error bodies.
 */
@OptIn(ExperimentalStdlibApi::class)
public class InventoryRetrofitClientKotlinModelTest {

    /** Where a product is stocked. */
    public enum class Warehouse {
        @Json(name = "north")
        NORTH,

        @Json(name = "south")
        SOUTH,
    }

    /** A catalog product as the service returns it. */
    public data class Product(
        val sku: String,
        val name: String,
        @Json(name = "price_cents") val priceCents: Long,
        @Json(name = "on_hand") val onHand: Int = 0,
        val warehouse: Warehouse = Warehouse.NORTH,
        val discontinued: Boolean = false,
    )

    /** Generic page envelope the service wraps every listing in. */
    public data class Page<T>(
        val items: List<T>,
        val total: Int,
        @Json(name = "next_cursor") val nextCursor: String? = null,
    )

    /** Body the dashboard posts to restock a product. */
    public data class RestockRequest(
        val quantity: Int,
        @Json(name = "purchase_order") val purchaseOrder: String,
        val urgent: Boolean = false,
    )

    /** Partial update that only carries the fields being changed. */
    public data class ProductPatch(
        @Json(name = "price_cents") val priceCents: Long? = null,
        val discontinued: Boolean? = null,
    )

    /** Stock level the service reports after a restock. */
    public data class StockLevel(
        val sku: String,
        @Json(name = "on_hand") val onHand: Int,
        @Json(name = "pending_orders") val pendingOrders: List<String> = emptyList(),
    )

    /** Structured error body the service sends with 4xx responses. */
    public data class ApiError(
        val code: String,
        val message: String,
        val fields: Map<String, String> = emptyMap(),
    )

    /** The inventory service as the dashboard declares it. */
    public interface InventoryApi {
        @GET("products/{sku}")
        suspend fun product(@Path("sku") sku: String): Product

        @GET("products")
        suspend fun search(@Query("q") query: String, @Query("cursor") cursor: String?): Page<Product>

        @POST("products/{sku}/restock")
        suspend fun restock(@Path("sku") sku: String, @Body request: RestockRequest): Response<StockLevel>

        @PATCH("products/{sku}")
        suspend fun update(@Path("sku") sku: String, @Body patch: ProductPatch): Product
    }

    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val server: MockWebServer = MockWebServer()

    private lateinit var api: InventoryApi

    @BeforeEach
    fun startServer() {
        server.start()
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        api = retrofit.create<InventoryApi>()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun enqueueJson(code: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    private fun takeRequest(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!

    @Test
    fun lookupDecodesProductWithRenamedFieldsAndDefaults() = runBlocking {
        enqueueJson(200, """{"sku":"BOLT-M6","name":"M6 bolt","price_cents":35,"on_hand":1200}""")

        val product: Product = api.product("BOLT-M6")

        assertEquals(Product("BOLT-M6", "M6 bolt", 35L, 1200, Warehouse.NORTH, false), product)
        val request: RecordedRequest = takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/products/BOLT-M6", request.path)
    }

    @Test
    fun searchPagesThroughGenericEnvelopeOfProducts() = runBlocking {
        enqueueJson(
            200,
            """
            {"items":[
              {"sku":"NUT-M6","name":"M6 nut","price_cents":12,"on_hand":5000,"warehouse":"south"},
              {"sku":"WASHER-M6","name":"M6 washer","price_cents":4}
            ],"total":3,"next_cursor":"c2"}
            """.trimIndent(),
        )
        enqueueJson(
            200,
            """{"items":[{"sku":"BOLT-M6","name":"M6 bolt","price_cents":35,"discontinued":true}],"total":3}""",
        )

        val first: Page<Product> = api.search("m6", null)
        val second: Page<Product> = api.search("m6", first.nextCursor)

        assertEquals(3, first.total)
        assertEquals("c2", first.nextCursor)
        assertEquals(
            listOf(
                Product("NUT-M6", "M6 nut", 12L, 5000, Warehouse.SOUTH),
                Product("WASHER-M6", "M6 washer", 4L),
            ),
            first.items,
        )
        assertNull(second.nextCursor)
        assertEquals(listOf("BOLT-M6"), second.items.map { it.sku })
        assertEquals(true, second.items.single().discontinued)
        val allSkus: List<String> = (first.items + second.items).map { it.sku }
        assertEquals(listOf("NUT-M6", "WASHER-M6", "BOLT-M6"), allSkus)
        assertEquals("/api/products?q=m6", takeRequest().path)
        assertEquals("/api/products?q=m6&cursor=c2", takeRequest().path)
    }

    @Test
    fun restockEncodesKotlinBodyAndDecodesStockLevel() = runBlocking {
        enqueueJson(201, """{"sku":"NUT-M6","on_hand":5500,"pending_orders":["so-1","so-2"]}""")

        val response: Response<StockLevel> = api.restock("NUT-M6", RestockRequest(500, "PO-77", urgent = true))

        assertEquals(201, response.code())
        assertEquals(StockLevel("NUT-M6", 5500, listOf("so-1", "so-2")), response.body())
        val request: RecordedRequest = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/products/NUT-M6/restock", request.path)
        assertEquals("application/json; charset=UTF-8", request.getHeader("Content-Type"))
        assertEquals("""{"quantity":500,"purchase_order":"PO-77","urgent":true}""", request.body.readUtf8())
    }

    @Test
    fun patchBodyOmitsNullFieldsSoOnlyChangesAreSent() = runBlocking {
        enqueueJson(200, """{"sku":"WASHER-M6","name":"M6 washer","price_cents":5}""")

        val updated: Product = api.update("WASHER-M6", ProductPatch(priceCents = 5L))

        assertEquals(5L, updated.priceCents)
        assertFalse(updated.discontinued)
        val request: RecordedRequest = takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("""{"price_cents":5}""", request.body.readUtf8())
    }

    @Test
    fun rejectedRestockExposesStructuredErrorBody() = runBlocking {
        enqueueJson(
            422,
            """{"code":"invalid_quantity","message":"Quantity must be positive","fields":{"quantity":"-3"}}""",
        )

        val response: Response<StockLevel> = api.restock("NUT-M6", RestockRequest(-3, "PO-78"))

        assertFalse(response.isSuccessful)
        assertNull(response.body())
        val errorAdapter: JsonAdapter<ApiError> = moshi.adapter<ApiError>()
        val error: ApiError = errorAdapter.fromJson(response.errorBody()!!.string())!!
        assertEquals(
            ApiError("invalid_quantity", "Quantity must be positive", mapOf("quantity" to "-3")),
            error,
        )
        assertEquals("""{"quantity":-3,"purchase_order":"PO-78","urgent":false}""", takeRequest().body.readUtf8())
    }

    @Test
    fun missingProductSurfacesAsHttpExceptionFromSuspendCall() {
        enqueueJson(404, """{"code":"not_found","message":"No product GHOST"}""")

        val failure: HttpException = assertThrows(HttpException::class.java) {
            runBlocking { api.product("GHOST") }
        }

        assertEquals(404, failure.code())
        val error: ApiError = moshi.adapter<ApiError>().fromJson(failure.response()!!.errorBody()!!.string())!!
        assertEquals("not_found", error.code)
        assertEquals(emptyMap<String, String>(), error.fields)
    }

    @Test
    fun responseMissingRequiredFieldFailsTheCallWithItsJsonName() {
        enqueueJson(200, """{"sku":"BOLT-M8","name":"M8 bolt","on_hand":40}""")

        val failure: JsonDataException = assertThrows(JsonDataException::class.java) {
            runBlocking { api.product("BOLT-M8") }
        }

        assertEquals("Required value 'priceCents' (JSON name 'price_cents') missing at \$", failure.message)
    }
}
