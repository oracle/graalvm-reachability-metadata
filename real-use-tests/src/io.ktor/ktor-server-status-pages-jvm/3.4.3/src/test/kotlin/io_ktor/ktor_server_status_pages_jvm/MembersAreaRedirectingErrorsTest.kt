/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Status and exception handlers that answer errors with redirects, where a
 * members-area site sends browsers to the page they should see instead.
 *
 * A 401 `status` handler redirects to the login page with the original path as
 * the return target, an exception handler answers a renamed article with a
 * permanent redirect, and a failed form post is answered Post/Redirect/Get style
 * with a 303; clients that follow redirects land on the target pages.
 */
public class MembersAreaRedirectingErrorsTest {
    /** Thrown when an article is requested by a slug it has since been renamed from. */
    private class ArticleMoved(val currentSlug: String) : RuntimeException("Article moved to $currentSlug")

    /** Thrown when a form is posted with a session that is no longer valid. */
    private class SessionExpired(val formPath: String) : RuntimeException("Session expired on $formPath")

    /** The members area, with the redirects its error handlers answer with. */
    private fun ApplicationTestBuilder.membersArea() {
        application {
            install(StatusPages) {
                status(HttpStatusCode.Unauthorized) { call, _ ->
                    call.respondRedirect("/login?returnTo=${call.request.uri.encodeURLParameter()}")
                }
                exception<ArticleMoved> { call, cause ->
                    call.respondRedirect("/articles/${cause.currentSlug}", permanent = true)
                }
                exception<SessionExpired> { call, cause ->
                    call.response.header(HttpHeaders.Location, "/login?expired=true&returnTo=${cause.formPath}")
                    call.respond(HttpStatusCode.SeeOther)
                }
            }
            routing {
                get("/login") {
                    val returnTo: String = call.request.queryParameters["returnTo"] ?: "/"
                    val expired: Boolean = call.request.queryParameters["expired"] == "true"
                    val notice: String = if (expired) "Your session expired. " else ""
                    call.respondText("${notice}Sign in to continue to $returnTo")
                }
                get("/account/orders") {
                    if (call.request.headers[HttpHeaders.Authorization] != MEMBER_TOKEN) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }
                    val page: String = call.request.queryParameters["page"] ?: "1"
                    call.respondText("Orders page $page for member 42")
                }
                get("/articles/{slug}") {
                    val slug: String = call.parameters["slug"]!!
                    val currentSlug: String? = RENAMED_ARTICLES[slug]
                    if (currentSlug != null) {
                        throw ArticleMoved(currentSlug)
                    }
                    call.respondText("Article: $slug")
                }
                post("/forum/replies") {
                    val reply: String = call.receiveText()
                    if (call.request.headers[HttpHeaders.Authorization] != MEMBER_TOKEN) {
                        throw SessionExpired("/forum")
                    }
                    call.respondText("Posted: $reply", status = HttpStatusCode.Created)
                }
            }
        }
    }

    /** A client that reports redirects instead of following them. */
    private fun ApplicationTestBuilder.nonFollowingClient(): HttpClient = createClient {
        followRedirects = false
    }

    @Test
    fun unauthorizedPageRedirectsToLoginWithReturnPath() = testApplication {
        membersArea()

        val response: HttpResponse = nonFollowingClient().get("/account/orders?page=2")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/login?returnTo=%2Faccount%2Forders%3Fpage%3D2", response.headers[HttpHeaders.Location])
    }

    @Test
    fun browserFollowingTheLoginRedirectLandsOnTheSignInPage() = testApplication {
        membersArea()

        val response: HttpResponse = client.get("/account/orders?page=2")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Sign in to continue to /account/orders?page=2", response.bodyAsText())
    }

    @Test
    fun signedInMemberIsNotRedirected() = testApplication {
        membersArea()

        val response: HttpResponse = nonFollowingClient().get("/account/orders?page=3") {
            header(HttpHeaders.Authorization, MEMBER_TOKEN)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.headers[HttpHeaders.Location])
        assertEquals("Orders page 3 for member 42", response.bodyAsText())
    }

    @Test
    fun renamedArticleIsMovedPermanently() = testApplication {
        membersArea()

        val redirect: HttpResponse = nonFollowingClient().get("/articles/ktor-2-migration")
        val followed: HttpResponse = client.get("/articles/ktor-2-migration")
        val current: HttpResponse = client.get("/articles/ktor-3-migration")

        assertEquals(HttpStatusCode.MovedPermanently, redirect.status)
        assertEquals("/articles/ktor-3-migration", redirect.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.OK, followed.status)
        assertEquals("Article: ktor-3-migration", followed.bodyAsText())
        assertEquals("Article: ktor-3-migration", current.bodyAsText())
    }

    @Test
    fun expiredSessionFormPostIsAnsweredWithSeeOther() = testApplication {
        membersArea()

        val expired: HttpResponse = nonFollowingClient().post("/forum/replies") {
            setBody("Thanks for the write-up")
        }
        val signedIn: HttpResponse = nonFollowingClient().post("/forum/replies") {
            header(HttpHeaders.Authorization, MEMBER_TOKEN)
            setBody("Thanks for the write-up")
        }
        val loginPage: HttpResponse = client.get(expired.headers[HttpHeaders.Location]!!)

        assertEquals(HttpStatusCode.SeeOther, expired.status)
        assertEquals("/login?expired=true&returnTo=/forum", expired.headers[HttpHeaders.Location])
        assertEquals("Your session expired. Sign in to continue to /forum", loginPage.bodyAsText())
        assertEquals(HttpStatusCode.Created, signedIn.status)
        assertEquals("Posted: Thanks for the write-up", signedIn.bodyAsText())
    }

    private companion object {
        const val MEMBER_TOKEN: String = "Bearer member-42"

        val RENAMED_ARTICLES: Map<String, String> = mapOf("ktor-2-migration" to "ktor-3-migration")
    }
}
