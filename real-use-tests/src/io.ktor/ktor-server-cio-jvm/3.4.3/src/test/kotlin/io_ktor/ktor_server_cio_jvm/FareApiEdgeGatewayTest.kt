/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The CIO engine's API edge plugins: CORS preflight and origin checks, per-client rate
 * limiting keyed on forwarded client addresses, X-Forwarded header resolution, and default
 * response headers.
 *
 * A transit agency publishes a fare API behind a reverse proxy. The proxy forwards the
 * rider's address, the public scheme and the public host; a browser storefront on another
 * origin preflights a fare update, reads fares with credentials, and a foreign origin is
 * refused. Each rider gets three quote requests per hour and the fourth is turned away with
 * `429`. The test checks the CORS response headers, the resolved request origin, the
 * rate-limit headers and rejection, and the stamped default headers.
 */
class FareApiEdgeGatewayTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val fares: MutableMap<String, String> = ConcurrentHashMap(mapOf("zone-a" to "2.40", "zone-b" to "3.10"))

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(DefaultHeaders) {
                header("X-Edge-Node", "fare-gateway-1")
            }
            install(XForwardedHeaders)
            install(CORS) {
                allowHost(STOREFRONT_HOST, schemes = listOf("https"))
                allowMethod(HttpMethod.Put)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(API_KEY_HEADER)
                exposeHeader(FARE_VERSION_HEADER)
                allowCredentials = true
                maxAgeInSeconds = 600
            }
            install(RateLimit) {
                register(QUOTES) {
                    rateLimiter(limit = 3, refillPeriod = 1.hours)
                    requestKey { call -> call.request.origin.remoteAddress }
                }
            }
            routing {
                get("/fares/{zone}") {
                    val zone: String = call.parameters["zone"]!!
                    call.response.header(FARE_VERSION_HEADER, "v${fares.size}")
                    call.respondText(fares[zone] ?: "unknown", ContentType.Text.Plain)
                }
                put("/fares/{zone}") {
                    val zone: String = call.parameters["zone"]!!
                    fares[zone] = call.receiveText()
                    call.respondText("updated $zone", ContentType.Text.Plain)
                }
                get("/whoami") {
                    val origin = call.request.origin
                    call.respondText(
                        "${origin.scheme}|${origin.serverHost}|${origin.serverPort}|${origin.remoteAddress}",
                        ContentType.Text.Plain,
                    )
                }
                rateLimit(QUOTES) {
                    get("/quotes") {
                        call.respondText("quote for ${call.request.origin.remoteAddress}", ContentType.Text.Plain)
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun storefrontAndRidersPassThroughTheFareApiEdge() {
        HttpClient(ClientCIO).use { client: HttpClient ->
            runBlocking {
                val base = "http://127.0.0.1:$port"

                val preflight: HttpResponse = client.options("$base/fares/zone-a") {
                    header(HttpHeaders.Origin, STOREFRONT)
                    header(HttpHeaders.AccessControlRequestMethod, "PUT")
                    header(HttpHeaders.AccessControlRequestHeaders, "content-type,x-api-key")
                }
                assertEquals(HttpStatusCode.OK, preflight.status)
                assertEquals(STOREFRONT, preflight.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("true", preflight.headers[HttpHeaders.AccessControlAllowCredentials])
                assertEquals("600", preflight.headers[HttpHeaders.AccessControlMaxAge])
                val allowedMethods: String = preflight.headers[HttpHeaders.AccessControlAllowMethods].orEmpty()
                assertTrue(allowedMethods.contains("PUT"), allowedMethods)
                val allowedHeaders: String = preflight.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty().lowercase()
                assertTrue(allowedHeaders.contains("x-api-key"), allowedHeaders)
                assertTrue(allowedHeaders.contains("content-type"), allowedHeaders)
                assertEquals("fare-gateway-1", preflight.headers["X-Edge-Node"])

                val update: HttpResponse = client.put("$base/fares/zone-c") {
                    header(HttpHeaders.Origin, STOREFRONT)
                    header(API_KEY_HEADER, "storefront-key")
                    contentType(ContentType.Text.Plain)
                    setBody("4.75")
                }
                assertEquals(HttpStatusCode.OK, update.status)
                assertEquals("updated zone-c", update.bodyAsText())
                assertEquals(STOREFRONT, update.headers[HttpHeaders.AccessControlAllowOrigin])

                val read: HttpResponse = client.get("$base/fares/zone-c") {
                    header(HttpHeaders.Origin, STOREFRONT)
                }
                assertEquals(HttpStatusCode.OK, read.status)
                assertEquals("4.75", read.bodyAsText())
                assertEquals(STOREFRONT, read.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("v3", read.headers[FARE_VERSION_HEADER])
                val exposed: String = read.headers[HttpHeaders.AccessControlExposeHeaders].orEmpty()
                assertTrue(exposed.contains(FARE_VERSION_HEADER), exposed)

                val insecureStorefront: HttpResponse = client.get("$base/fares/zone-a") {
                    header(HttpHeaders.Origin, "http://$STOREFRONT_HOST")
                }
                assertEquals(HttpStatusCode.Forbidden, insecureStorefront.status)
                assertNull(insecureStorefront.headers[HttpHeaders.AccessControlAllowOrigin])

                val foreignPreflight: HttpResponse = client.options("$base/fares/zone-a") {
                    header(HttpHeaders.Origin, "https://scraper.example.net")
                    header(HttpHeaders.AccessControlRequestMethod, "PUT")
                }
                assertEquals(HttpStatusCode.Forbidden, foreignPreflight.status)
                assertNull(foreignPreflight.headers[HttpHeaders.AccessControlAllowOrigin])

                val deleteRequested: HttpResponse = client.options("$base/fares/zone-a") {
                    header(HttpHeaders.Origin, STOREFRONT)
                    header(HttpHeaders.AccessControlRequestMethod, "DELETE")
                }
                assertEquals(HttpStatusCode.Forbidden, deleteRequested.status)
                assertEquals("2.40", fares["zone-a"])

                val forwarded: HttpResponse = client.get("$base/whoami") {
                    header(HttpHeaders.XForwardedFor, RIDER_ONE)
                    header(HttpHeaders.XForwardedProto, "https")
                    header(HttpHeaders.XForwardedHost, "fares.transit.example.org")
                }
                assertEquals("https|fares.transit.example.org|443|$RIDER_ONE", forwarded.bodyAsText())
                assertNotNull(forwarded.headers[HttpHeaders.Date])

                val direct: HttpResponse = client.get("$base/whoami")
                assertEquals("http|127.0.0.1|$port|127.0.0.1", direct.bodyAsText())

                val remaining: List<String?> = (1..3).map {
                    val quote: HttpResponse = client.get("$base/quotes") {
                        header(HttpHeaders.XForwardedFor, RIDER_ONE)
                    }
                    assertEquals(HttpStatusCode.OK, quote.status)
                    assertEquals("quote for $RIDER_ONE", quote.bodyAsText())
                    assertEquals("3", quote.headers["X-RateLimit-Limit"])
                    quote.headers["X-RateLimit-Remaining"]
                }
                assertEquals(listOf("2", "1", "0"), remaining)

                val throttled: HttpResponse = client.get("$base/quotes") {
                    header(HttpHeaders.XForwardedFor, RIDER_ONE)
                }
                assertEquals(HttpStatusCode.TooManyRequests, throttled.status)
                val retryAfter: Long = throttled.headers[HttpHeaders.RetryAfter]!!.toLong()
                assertTrue(retryAfter in 1..3_600, "Retry-After $retryAfter")
                assertEquals("fare-gateway-1", throttled.headers["X-Edge-Node"])

                val otherRider: HttpResponse = client.get("$base/quotes") {
                    header(HttpHeaders.XForwardedFor, RIDER_TWO)
                }
                assertEquals(HttpStatusCode.OK, otherRider.status)
                assertEquals("quote for $RIDER_TWO", otherRider.bodyAsText())
                assertEquals("2", otherRider.headers["X-RateLimit-Remaining"])

                val unlimited: HttpResponse = client.get("$base/fares/zone-b") {
                    header(HttpHeaders.XForwardedFor, RIDER_ONE)
                }
                assertEquals(HttpStatusCode.OK, unlimited.status)
                assertEquals("3.10", unlimited.bodyAsText())
                assertNull(unlimited.headers["X-RateLimit-Limit"])
            }
        }

        assertEquals(mapOf("zone-a" to "2.40", "zone-b" to "3.10", "zone-c" to "4.75"), fares.toMap())
    }

    private companion object {
        const val STOREFRONT_HOST: String = "shop.transit.example.org"
        const val STOREFRONT: String = "https://$STOREFRONT_HOST"
        const val API_KEY_HEADER: String = "X-Api-Key"
        const val FARE_VERSION_HEADER: String = "X-Fare-Version"
        const val RIDER_ONE: String = "203.0.113.17"
        const val RIDER_TWO: String = "198.51.100.42"
        val QUOTES: RateLimitName = RateLimitName("quotes")
    }
}
