/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.p
import kotlinx.html.title
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Status-code handlers that rewrite plain error statuses into HTML error pages,
 * for a bookshop site whose routes answer with bare statuses.
 *
 * A `status` handler renders the 404 page for any unknown path, a
 * `StatusContext` handler reads the original response body to explain a
 * 401 or 403, and statuses without a handler pass through untouched.
 */
public class BookshopStatusPagesTest {
    /** Every page the bookshop serves, as the routing tree builds them. */
    private fun ApplicationTestBuilder.bookshop() {
        application {
            install(StatusPages) {
                status(HttpStatusCode.NotFound) { call, status ->
                    call.respondHtml(status) {
                        head { title { +"Bookshop - ${status.value}" } }
                        body {
                            h1 { +"Page not found" }
                            p { +"Nothing lives at ${call.request.local.uri}" }
                        }
                    }
                }
                status(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden) { status ->
                    val reason: String = (content as? TextContent)?.text ?: "no reason given"
                    call.respondHtml(status) {
                        head { title { +"Bookshop - ${status.value}" } }
                        body {
                            h1 { +"Access denied (${status.description})" }
                            p { +reason }
                        }
                    }
                }
            }
            routing {
                get("/catalog") {
                    call.respondText("Dune; Solaris; Neuromancer")
                }
                get("/orders") {
                    call.respond(HttpStatusCode.Unauthorized)
                }
                get("/admin/stock") {
                    call.respondText("The admin role is required", status = HttpStatusCode.Forbidden)
                }
                get("/books/{isbn}") {
                    val isbn: String = call.parameters["isbn"]!!
                    if (isbn in RETIRED_ISBNS) {
                        call.respondText("Out of print", status = HttpStatusCode.Gone)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }

    @Test
    fun successfulPageIsServedAsIs() = testApplication {
        bookshop()

        val response: HttpResponse = client.get("/catalog")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Plain))
        assertEquals("Dune; Solaris; Neuromancer", response.bodyAsText())
    }

    @Test
    fun unknownPathAndMissingBookShareTheNotFoundPage() = testApplication {
        bookshop()

        val unknownPath: HttpResponse = client.get("/gift-cards")
        val missingBook: HttpResponse = client.get("/books/978-0000000000")

        assertEquals(HttpStatusCode.NotFound, unknownPath.status)
        assertTrue(unknownPath.contentType()!!.match(ContentType.Text.Html))
        val unknownPage: String = unknownPath.bodyAsText()
        assertTrue(unknownPage.contains("<title>Bookshop - 404</title>"), unknownPage)
        assertTrue(unknownPage.contains("<h1>Page not found</h1>"), unknownPage)
        assertTrue(unknownPage.contains("<p>Nothing lives at /gift-cards</p>"), unknownPage)

        assertEquals(HttpStatusCode.NotFound, missingBook.status)
        val missingPage: String = missingBook.bodyAsText()
        assertTrue(missingPage.contains("<p>Nothing lives at /books/978-0000000000</p>"), missingPage)
    }

    @Test
    fun accessHandlerExplainsTheOriginalResponse() = testApplication {
        bookshop()

        val anonymous: HttpResponse = client.get("/orders")
        val clerk: HttpResponse = client.get("/admin/stock")

        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertTrue(anonymous.contentType()!!.match(ContentType.Text.Html))
        val anonymousPage: String = anonymous.bodyAsText()
        assertTrue(anonymousPage.contains("<h1>Access denied (Unauthorized)</h1>"), anonymousPage)
        assertTrue(anonymousPage.contains("<p>no reason given</p>"), anonymousPage)

        assertEquals(HttpStatusCode.Forbidden, clerk.status)
        val clerkPage: String = clerk.bodyAsText()
        assertTrue(clerkPage.contains("<title>Bookshop - 403</title>"), clerkPage)
        assertTrue(clerkPage.contains("<h1>Access denied (Forbidden)</h1>"), clerkPage)
        assertTrue(clerkPage.contains("<p>The admin role is required</p>"), clerkPage)
    }

    @Test
    fun statusWithoutHandlerPassesThrough() = testApplication {
        bookshop()

        val response: HttpResponse = client.get("/books/${RETIRED_ISBNS.first()}")

        assertEquals(HttpStatusCode.Gone, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Plain))
        assertEquals("Out of print", response.bodyAsText())
    }

    private companion object {
        val RETIRED_ISBNS: Set<String> = setOf("978-0441013593")
    }
}
