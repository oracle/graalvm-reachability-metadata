/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bearer and Basic authentication through the client Auth plugin, as an accounting application
 * calls a billing API that expires access tokens and refreshes them at its token endpoint.
 */
public class BillingApiAuthTest {

    /** The billing API: issues and revokes bearer tokens, and guards its admin area with Basic auth. */
    class BillingService : AutoCloseable {
        private val accessTokens: ConcurrentHashMap<String, String> = ConcurrentHashMap(mapOf("access-1" to "acme"))
        private val refreshTokens: ConcurrentHashMap<String, String> = ConcurrentHashMap(mapOf("refresh-1" to "acme"))
        private val issued: AtomicInteger = AtomicInteger(1)

        /** Every `Authorization` header an authenticated invoice request carried, in arrival order. */
        val invoiceAuthorizations: MutableList<String> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(Authentication) {
                bearer("api") {
                    realm = "billing"
                    authenticate { credential -> accessTokens[credential.token]?.let { UserIdPrincipal(it) } }
                }
                basic("admin") {
                    realm = "billing-admin"
                    validate { credential ->
                        if (credential.name == "auditor" && credential.password == "s3cret") UserIdPrincipal(credential.name) else null
                    }
                }
            }
            routing {
                post("/oauth/token") {
                    val form = call.receiveParameters()
                    val account: String? = refreshTokens.remove(form["refresh_token"] ?: "")
                    if (form["grant_type"] != "refresh_token" || account == null) {
                        call.respondText("invalid_grant", status = HttpStatusCode.BadRequest)
                        return@post
                    }
                    val generation: Int = issued.incrementAndGet()
                    accessTokens["access-$generation"] = account
                    refreshTokens["refresh-$generation"] = account
                    call.respondText("access-$generation refresh-$generation")
                }
                authenticate("api") {
                    get("/invoices/{number}") {
                        invoiceAuthorizations += call.request.headers["Authorization"] ?: "<none>"
                        val account: String = call.principal<UserIdPrincipal>()!!.name
                        call.respondText("invoice ${call.parameters["number"]} for $account")
                    }
                }
                authenticate("admin") {
                    get("/admin/ledger") {
                        call.respondText("ledger opened by ${call.principal<UserIdPrincipal>()!!.name}")
                    }
                }
            }
        }

        val port: Int

        init {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        /** Expires every access token issued so far, as a token lifetime running out would. */
        fun expireAccessTokens() {
            accessTokens.clear()
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    @Test
    fun refreshesAnExpiredBearerTokenAndRetriesTheRequest(): Unit = runBlocking {
        BillingService().use { service: BillingService ->
            val refreshedFrom: MutableList<String?> = mutableListOf()
            val client: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${service.port}/") }
                install(Auth) {
                    bearer {
                        realm = "billing"
                        loadTokens { BearerTokens("access-1", "refresh-1") }
                        refreshTokens {
                            refreshedFrom += oldTokens?.refreshToken
                            val reply: HttpResponse = client.submitForm(
                                url = "oauth/token",
                                formParameters = parameters {
                                    append("grant_type", "refresh_token")
                                    append("refresh_token", oldTokens?.refreshToken ?: "")
                                },
                            ) { markAsRefreshTokenRequest() }
                            val (access: String, refresh: String) = reply.bodyAsText().split(' ')
                            BearerTokens(access, refresh)
                        }
                    }
                }
            }
            client.use {
                val first: HttpResponse = client.get("invoices/1001")
                assertThat(first.status).isEqualTo(HttpStatusCode.OK)
                assertThat(first.bodyAsText()).isEqualTo("invoice 1001 for acme")

                service.expireAccessTokens()
                val second: HttpResponse = client.get("invoices/1002")
                assertThat(second.status).isEqualTo(HttpStatusCode.OK)
                assertThat(second.bodyAsText()).isEqualTo("invoice 1002 for acme")

                val third: HttpResponse = client.get("invoices/1003")
                assertThat(third.bodyAsText()).isEqualTo("invoice 1003 for acme")
            }

            assertThat(refreshedFrom).containsExactly("refresh-1")
            assertThat(service.invoiceAuthorizations).containsExactly(
                "Bearer access-1",
                "Bearer access-2",
                "Bearer access-2",
            )
        }
    }

    @Test
    fun sendsBasicCredentialsOnlyToTheChallengingRealm(): Unit = runBlocking {
        BillingService().use { service: BillingService ->
            val auditor: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${service.port}/") }
                install(Auth) {
                    basic {
                        realm = "billing-admin"
                        credentials { BasicAuthCredentials(username = "auditor", password = "s3cret") }
                    }
                }
            }
            auditor.use {
                val ledger: HttpResponse = auditor.get("admin/ledger")
                assertThat(ledger.status).isEqualTo(HttpStatusCode.OK)
                assertThat(ledger.bodyAsText()).isEqualTo("ledger opened by auditor")

                // The bearer realm challenges with a different scheme, so no Basic credentials apply.
                val invoice: HttpResponse = auditor.get("invoices/2001")
                assertThat(invoice.status).isEqualTo(HttpStatusCode.Unauthorized)
                assertThat(invoice.headers["WWW-Authenticate"]).startsWith("Bearer")
            }

            val intruder: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${service.port}/") }
                install(Auth) {
                    basic {
                        credentials { BasicAuthCredentials(username = "auditor", password = "guess") }
                    }
                }
            }
            intruder.use {
                val rejected: HttpResponse = intruder.get("admin/ledger")
                assertThat(rejected.status).isEqualTo(HttpStatusCode.Unauthorized)
                assertThat(rejected.headers["WWW-Authenticate"]).isEqualTo("Basic realm=billing-admin, charset=UTF-8")
            }
            assertThat(service.invoiceAuthorizations).isEmpty()
        }
    }
}
