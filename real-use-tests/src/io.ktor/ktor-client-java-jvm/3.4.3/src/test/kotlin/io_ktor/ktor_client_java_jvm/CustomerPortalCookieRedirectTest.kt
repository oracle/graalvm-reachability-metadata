/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpResponseRedirectEvent
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.ConstantCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.addCookie
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.plugins.cookies.get
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Session cookies and redirect following through the client HttpCookies and HttpRedirect plugins
 * over the Java engine, as a customer portal logs in, moves between relocated pages, and hands
 * invoice downloads to a partner file host.
 */
public class CustomerPortalCookieRedirectTest {

    /** One embedded CIO server on a free loopback port, built from the given routes. */
    class Site(routes: Routing.() -> Unit) : AutoCloseable {
        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing(routes)
        }

        val port: Int

        val base: String
            get() = "http://127.0.0.1:$port/"

        init {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    /** The cookie names a request carried, sorted, so responses can echo them deterministically. */
    private fun ApplicationCall.cookieNames(): String = request.cookies.rawCookies.keys.sorted().joinToString(",")

    /** The portal: issues a session cookie at login, scopes a preference cookie, and expires the session. */
    private fun portal(): Site = Site {
        get("/login") {
            val user: String = call.request.queryParameters["user"] ?: "guest"
            call.response.cookies.append(Cookie(name = "session", value = "s-$user", path = "/", httpOnly = true))
            call.response.cookies.append(Cookie(name = "theme", value = "dark", path = "/prefs"))
            call.respondRedirect("/dashboard")
        }
        get("/dashboard") {
            val session: String? = call.request.cookies["session"]
            if (session == null) {
                call.respondText("login required", status = HttpStatusCode.Unauthorized)
            } else {
                call.respondText("welcome ${session.removePrefix("s-")}; cookies=${call.cookieNames()}")
            }
        }
        get("/prefs/theme") {
            call.respondText("theme=${call.request.cookies["theme"]}; cookies=${call.cookieNames()}")
        }
        get("/logout") {
            call.response.cookies.append(Cookie(name = "session", value = "", path = "/", expires = GMTDate(0L)))
            call.respondText("bye")
        }
    }

    @Test
    fun keepsTheLoginSessionAcrossRedirectsUntilLogout(): Unit = runBlocking {
        portal().use { site: Site ->
            val redirects: MutableList<HttpStatusCode> = CopyOnWriteArrayList()
            val client: HttpClient = HttpClient(Java) {
                defaultRequest { url(site.base) }
                install(HttpCookies) {
                    storage = AcceptAllCookiesStorage()
                    default { addCookie(site.base, Cookie(name = "consent", value = "granted", path = "/")) }
                }
            }
            client.monitor.subscribe(HttpResponseRedirectEvent) { response -> redirects += response.status }
            client.use {
                // The session cookie set on the redirect response is already sent to its target.
                val dashboard: HttpResponse = client.get("login") { url.parameters.append("user", "ada") }
                assertThat(dashboard.status).isEqualTo(HttpStatusCode.OK)
                assertThat(dashboard.request.url.encodedPath).isEqualTo("/dashboard")
                assertThat(dashboard.bodyAsText()).isEqualTo("welcome ada; cookies=consent,session")
                assertThat(redirects).containsExactly(HttpStatusCode.Found)

                val stored: List<Cookie> = client.cookies(site.base + "prefs/theme")
                assertThat(stored.map { it.name }).containsExactlyInAnyOrder("consent", "session", "theme")
                val session: Cookie = stored["session"]!!
                assertThat(session.value).isEqualTo("s-ada")
                assertThat(session.httpOnly).isTrue()
                assertThat(session.path).isEqualTo("/")

                // The path-scoped theme cookie only travels below /prefs.
                assertThat(client.get("prefs/theme").bodyAsText())
                    .isEqualTo("theme=dark; cookies=consent,session,theme")
                assertThat(client.cookies(site.base + "dashboard").map { it.name })
                    .containsExactlyInAnyOrder("consent", "session")

                assertThat(client.get("logout").bodyAsText()).isEqualTo("bye")
                assertThat(client.cookies(site.base + "dashboard").map { it.name }).containsExactly("consent")
                val afterLogout: HttpResponse = client.get("dashboard")
                assertThat(afterLogout.status).isEqualTo(HttpStatusCode.Unauthorized)
                assertThat(afterLogout.bodyAsText()).isEqualTo("login required")
            }
        }
    }

    @Test
    fun aKioskWithConstantCookiesIgnoresTheCookiesTheServerSets(): Unit = runBlocking {
        portal().use { site: Site ->
            val client: HttpClient = HttpClient(Java) {
                defaultRequest { url(site.base) }
                install(HttpCookies) {
                    storage = ConstantCookiesStorage(
                        Cookie(name = "session", value = "s-kiosk", domain = "127.0.0.1", path = "/"),
                    )
                }
            }
            client.use {
                // The login redirect still lands on the dashboard, but with the kiosk's fixed session.
                val dashboard: HttpResponse = client.get("login") { url.parameters.append("user", "mallory") }
                assertThat(dashboard.bodyAsText()).isEqualTo("welcome kiosk; cookies=session")
                assertThat(client.get("prefs/theme").bodyAsText()).isEqualTo("theme=null; cookies=session")
                assertThat(client.cookies(site.base).map { "${it.name}=${it.value}" })
                    .containsExactly("session=s-kiosk")
            }
        }
    }

    /** The help center: relocated pages, a form post that answers 303, a moved payment endpoint, and a loop. */
    private fun helpCenter(received: MutableList<String>): Site = Site {
        get("/faq") {
            call.respondRedirect("/help/faq", permanent = true)
        }
        get("/help/faq") {
            call.response.header(HttpHeaders.Location, "articles/faq")
            call.respondText("", status = HttpStatusCode.PermanentRedirect)
        }
        get("/help/articles/faq") {
            call.respondText("Frequently asked questions")
        }
        post("/tickets") {
            received += "ticket:${call.receiveText()}"
            call.response.header(HttpHeaders.Location, "/tickets/42")
            call.respondText("", status = HttpStatusCode.SeeOther)
        }
        get("/tickets/42") {
            call.respondText("ticket 42: open")
        }
        post("/payments") {
            received += "payments:${call.receiveText()}"
            call.response.header(HttpHeaders.Location, "/payments/v2")
            call.respondText("", status = HttpStatusCode.TemporaryRedirect)
        }
        post("/payments/v2") {
            received += "payments/v2:${call.receiveText()}"
            call.respondText("payment accepted", status = HttpStatusCode.Created)
        }
        get("/loop") {
            call.respondRedirect("/loop")
        }
    }

    @Test
    fun followsRelocatedPagesAndLeavesFormPostsToTheApplication(): Unit = runBlocking {
        val received: MutableList<String> = CopyOnWriteArrayList()
        helpCenter(received).use { site: Site ->
            val redirects: MutableList<String> = CopyOnWriteArrayList()
            val client: HttpClient = HttpClient(Java) {
                defaultRequest { url(site.base) }
                install(HttpSend) { maxSendCount = 5 }
            }
            client.monitor.subscribe(HttpResponseRedirectEvent) { response ->
                redirects += "${response.status.value} ${response.request.url.encodedPath}"
            }
            client.use {
                // A 301 then a 308 with a relative Location both resolve against the current page.
                val faq: HttpResponse = client.get("faq")
                assertThat(faq.status).isEqualTo(HttpStatusCode.OK)
                assertThat(faq.bodyAsText()).isEqualTo("Frequently asked questions")
                assertThat(faq.request.url.encodedPath).isEqualTo("/help/articles/faq")
                assertThat(redirects).containsExactly("301 /faq", "308 /help/faq")

                // By default only GET and HEAD are redirected, so the 303 reaches the application.
                val ticket: HttpResponse = client.post("tickets") { setBody("printer jammed") }
                assertThat(ticket.status).isEqualTo(HttpStatusCode.SeeOther)
                assertThat(ticket.headers[HttpHeaders.Location]).isEqualTo("/tickets/42")
                assertThat(client.get(ticket.headers[HttpHeaders.Location]!!).bodyAsText()).isEqualTo("ticket 42: open")

                // A redirect loop is cut off once the send budget is spent.
                val loop: SendCountExceedException = assertThrows<SendCountExceedException> { client.get("loop") }
                assertThat(loop.message).contains("5")
            }

            val unchecked: HttpClient = HttpClient(Java) {
                defaultRequest { url(site.base) }
                install(HttpRedirect) { checkHttpMethod = false }
            }
            unchecked.use {
                // Without the method check a 307 replays the POST with its body to the new endpoint.
                val payment: HttpResponse = unchecked.post("payments") { setBody("amount=19.90") }
                assertThat(payment.status).isEqualTo(HttpStatusCode.Created)
                assertThat(payment.bodyAsText()).isEqualTo("payment accepted")
                assertThat(payment.request.method).isEqualTo(HttpMethod.Post)
                assertThat(payment.request.url.encodedPath).isEqualTo("/payments/v2")
            }

            val manual: HttpClient = HttpClient(Java) {
                defaultRequest { url(site.base) }
                followRedirects = false
            }
            manual.use {
                val moved: HttpResponse = manual.get("faq")
                assertThat(moved.status).isEqualTo(HttpStatusCode.MovedPermanently)
                assertThat(moved.headers[HttpHeaders.Location]).isEqualTo("/help/faq")
            }
            assertThat(received).containsExactly(
                "ticket:printer jammed",
                "payments:amount=19.90",
                "payments/v2:amount=19.90",
            )
        }
    }

    @Test
    fun dropsTheBearerTokenWhenAnInvoiceRedirectLeavesThePortal(): Unit = runBlocking {
        Site {
            get("/files/{name}") {
                val authorization: String = call.request.headers[HttpHeaders.Authorization] ?: "none"
                call.respondText("file ${call.parameters["name"]}; authorization=$authorization")
            }
        }.use { files: Site ->
            Site {
                get("/invoices/{id}/pdf") {
                    call.respondRedirect("${files.base}files/invoice-${call.parameters["id"]}.pdf")
                }
                get("/invoices/{id}") {
                    call.respondRedirect("/invoices/${call.parameters["id"]}/summary")
                }
                get("/invoices/{id}/summary") {
                    val authorization: String = call.request.headers[HttpHeaders.Authorization] ?: "none"
                    call.respondText("invoice ${call.parameters["id"]}; authorization=$authorization")
                }
            }.use { billing: Site ->
                val client: HttpClient = HttpClient(Java) {
                    defaultRequest { url(billing.base) }
                }
                client.use {
                    // A redirect within the portal keeps the credentials.
                    val summary: HttpResponse = client.get("invoices/7") { bearerAuth("portal-token") }
                    assertThat(summary.bodyAsText()).isEqualTo("invoice 7; authorization=Bearer portal-token")

                    // A redirect to another authority strips them before the file host sees the request.
                    val pdf: HttpResponse = client.get("invoices/7/pdf") { bearerAuth("portal-token") }
                    assertThat(pdf.status).isEqualTo(HttpStatusCode.OK)
                    assertThat(pdf.request.url.port).isEqualTo(files.port)
                    assertThat(pdf.bodyAsText()).isEqualTo("file invoice-7.pdf; authorization=none")
                }
            }
        }
    }
}
