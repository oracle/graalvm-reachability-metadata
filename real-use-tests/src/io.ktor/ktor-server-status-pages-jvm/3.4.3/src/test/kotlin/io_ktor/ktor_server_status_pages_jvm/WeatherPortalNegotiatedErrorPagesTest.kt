/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.acceptItems
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.p
import kotlinx.html.title
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Error pages whose representation the handlers negotiate from the request's
 * `Accept` header, where a weather portal serves browsers and API clients alike.
 *
 * The exception handler for an unknown city and the status handlers for the 404
 * and 405 that routing produces and the 406 that content negotiation produces
 * answer with a JSON problem document when the client prefers JSON, honoring
 * quality values, and with an HTML page otherwise, while successful forecasts
 * pass through untouched.
 */
public class WeatherPortalNegotiatedErrorPagesTest {
    /** The problem document API clients receive for every failed call. */
    @Serializable
    data class Problem(val status: Int, val title: String, val detail: String)

    /** A forecast as the portal publishes it. */
    @Serializable
    data class Forecast(val city: String, val highCelsius: Int, val sky: String)

    /** Raised when a forecast is asked for a city the portal does not track. */
    class UnknownCityException(val city: String) : RuntimeException("No forecast for $city")

    private val forecasts: Map<String, Forecast> = mapOf(
        "oslo" to Forecast("oslo", 9, "overcast"),
        "lisbon" to Forecast("lisbon", 24, "clear"),
    )

    /** The weather portal with its error pages, as the application module builds it. */
    private fun ApplicationTestBuilder.weatherPortal() {
        application {
            install(ContentNegotiation) {
                json(PORTAL_JSON)
            }
            install(StatusPages) {
                exception<UnknownCityException> { call, cause ->
                    call.respondProblem(HttpStatusCode.NotFound, "Unknown city", cause.message.orEmpty())
                }
                status(HttpStatusCode.NotFound) { call, status ->
                    call.respondProblem(status, "Page not found", "Nothing lives at ${call.request.local.uri}")
                }
                status(HttpStatusCode.NotAcceptable) { call, status ->
                    call.respondProblem(status, "Not acceptable", "${call.request.local.uri} is only published as JSON")
                }
                status(HttpStatusCode.MethodNotAllowed) { call, status ->
                    call.respondProblem(
                        status,
                        "Method not allowed",
                        "${call.request.local.method.value} is not supported on ${call.request.local.uri}",
                    )
                }
            }
            routing {
                get("/forecast/{city}") {
                    val city: String = call.parameters["city"]!!
                    call.respond(forecasts[city] ?: throw UnknownCityException(city))
                }
                post("/alerts") {
                    call.respond(HttpStatusCode.Accepted)
                }
            }
        }
    }

    /** Answers with JSON when the client ranks it above HTML, and with an HTML page otherwise. */
    private suspend fun ApplicationCall.respondProblem(status: HttpStatusCode, heading: String, detail: String) {
        val preferred: ContentType? = request.acceptItems()
            .map { item -> ContentType.parse(item.value) }
            .firstOrNull { type -> type.match(ContentType.Application.Json) || type.match(ContentType.Text.Html) }
        if (preferred != null && preferred.match(ContentType.Application.Json)) {
            respond(status, Problem(status.value, heading, detail))
        } else {
            respondHtml(status) {
                head { title { +"Weather portal - ${status.value}" } }
                body {
                    h1 { +heading }
                    p { +detail }
                }
            }
        }
    }

    @Test
    fun forecastForTrackedCityIsNotIntercepted() = testApplication {
        weatherPortal()

        val response: HttpResponse = client.get("/forecast/lisbon") {
            header(HttpHeaders.Accept, "application/json")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        assertEquals(Forecast("lisbon", 24, "clear"), PORTAL_JSON.decodeFromString<Forecast>(response.bodyAsText()))
    }

    @Test
    fun forecastRefusedToBrowserBecomesAnHtmlPage() = testApplication {
        weatherPortal()

        val response: HttpResponse = client.get("/forecast/oslo") {
            header(HttpHeaders.Accept, "text/html")
        }
        val page: String = response.bodyAsText()

        assertEquals(HttpStatusCode.NotAcceptable, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Html))
        assertTrue(page.contains("<h1>Not acceptable</h1>"), page)
        assertTrue(page.contains("<p>/forecast/oslo is only published as JSON</p>"), page)
    }

    @Test
    fun unknownCityIsAProblemDocumentForApiClients() = testApplication {
        weatherPortal()

        val response: HttpResponse = client.get("/forecast/atlantis") {
            header(HttpHeaders.Accept, "application/json")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        assertEquals(
            Problem(404, "Unknown city", "No forecast for atlantis"),
            PORTAL_JSON.decodeFromString<Problem>(response.bodyAsText()),
        )
    }

    @Test
    fun unknownCityIsAnHtmlPageForBrowsers() = testApplication {
        weatherPortal()

        val response: HttpResponse = client.get("/forecast/atlantis") {
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
        }
        val page: String = response.bodyAsText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Html))
        assertTrue(page.contains("<title>Weather portal - 404</title>"), page)
        assertTrue(page.contains("<h1>Unknown city</h1>"), page)
        assertTrue(page.contains("<p>No forecast for atlantis</p>"), page)
    }

    @Test
    fun qualityValuesDecideBetweenTheRepresentations() = testApplication {
        weatherPortal()

        val jsonPreferred: HttpResponse = client.get("/radar") {
            header(HttpHeaders.Accept, "text/html;q=0.4, application/json;q=0.9")
        }
        val htmlPreferred: HttpResponse = client.get("/radar") {
            header(HttpHeaders.Accept, "application/json;q=0.2, text/html")
        }

        assertEquals(HttpStatusCode.NotFound, jsonPreferred.status)
        assertTrue(jsonPreferred.contentType()!!.match(ContentType.Application.Json))
        assertEquals(
            Problem(404, "Page not found", "Nothing lives at /radar"),
            PORTAL_JSON.decodeFromString<Problem>(jsonPreferred.bodyAsText()),
        )
        assertEquals(HttpStatusCode.NotFound, htmlPreferred.status)
        assertTrue(htmlPreferred.contentType()!!.match(ContentType.Text.Html))
        assertTrue(htmlPreferred.bodyAsText().contains("<p>Nothing lives at /radar</p>"))
    }

    @Test
    fun clientWithoutAcceptHeaderGetsTheHtmlPage() = testApplication {
        weatherPortal()

        val response: HttpResponse = client.get("/radar")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Html))
        assertTrue(response.bodyAsText().contains("<h1>Page not found</h1>"))
    }

    @Test
    fun wrongMethodOnKnownPathIsNegotiatedToo() = testApplication {
        weatherPortal()

        val refused: HttpResponse = client.get("/alerts") {
            header(HttpHeaders.Accept, "application/json")
        }
        val accepted: HttpResponse = client.post("/alerts") {
            header(HttpHeaders.Accept, "application/json")
        }

        assertEquals(HttpStatusCode.MethodNotAllowed, refused.status)
        assertTrue(refused.contentType()!!.match(ContentType.Application.Json))
        assertEquals(
            Problem(405, "Method not allowed", "GET is not supported on /alerts"),
            PORTAL_JSON.decodeFromString<Problem>(refused.bodyAsText()),
        )
        assertEquals(HttpStatusCode.Accepted, accepted.status)
    }

    private companion object {
        val PORTAL_JSON: Json = Json { encodeDefaults = true }
    }
}
