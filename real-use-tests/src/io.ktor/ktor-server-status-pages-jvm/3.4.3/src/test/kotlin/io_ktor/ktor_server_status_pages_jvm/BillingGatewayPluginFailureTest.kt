/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.AttributeKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Exception handlers for failures thrown by other plugins' hooks rather than by
 * routes, where a multi-tenant billing gateway maps plugin-level refusals to errors.
 *
 * A custom tenant-resolution plugin throws from `onCall`, the bearer
 * authenticator throws for a revoked token, and a response-side redaction plugin
 * throws from `onCallRespond`; each failure reaches its `StatusPages` handler,
 * while an ordinary authentication challenge passes through untouched.
 */
public class BillingGatewayPluginFailureTest {
    /** Refusals raised while resolving the tenant a call belongs to. */
    private sealed class TenantException(message: String) : RuntimeException(message) {
        class Missing : TenantException("no tenant header")

        class Unknown(val tenant: String) : TenantException("unknown tenant $tenant")
    }

    /** A token that once was valid but has since been revoked by an administrator. */
    private class RevokedToken(val owner: String) : RuntimeException("token of $owner revoked")

    /** A response body that would leak an unmasked card number. */
    private class UnredactedCardNumber(val path: String) : RuntimeException("card number in $path")

    /** The billing gateway, its plugins, routes, and error mapping. */
    private fun ApplicationTestBuilder.billingGateway() {
        application {
            install(TenantResolution)
            install(CardRedaction)
            install(Authentication) {
                bearer("api") {
                    authenticate { credential ->
                        val revokedOwner: String? = REVOKED_TOKENS[credential.token]
                        if (revokedOwner != null) {
                            throw RevokedToken(revokedOwner)
                        }
                        ACTIVE_TOKENS[credential.token]?.let { owner -> UserIdPrincipal(owner) }
                    }
                }
            }
            install(StatusPages) {
                exception<TenantException.Missing> { call, _ ->
                    call.respondText("tenant header required", status = HttpStatusCode.BadRequest)
                }
                exception<TenantException.Unknown> { call, cause ->
                    call.respondText("no such tenant: ${cause.tenant}", status = HttpStatusCode.NotFound)
                }
                exception<RevokedToken> { call, cause ->
                    call.respondText(
                        "token of ${cause.owner} was revoked",
                        status = HttpStatusCode.Forbidden,
                    )
                }
                exception<UnredactedCardNumber> { call, cause ->
                    call.respondText(
                        "response from ${cause.path} withheld",
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            }
            routing {
                authenticate("api") {
                    get("/invoices") {
                        val tenant: String = call.attributes[TENANT]
                        val user: String = call.principal<UserIdPrincipal>()!!.name
                        call.respond("invoices of $tenant for $user")
                    }
                    get("/payment-methods") {
                        call.respond("card=4111111111111111")
                    }
                }
            }
        }
    }

    /** Requests [path] for [tenant] with [token], omitting whichever is null. */
    private suspend fun ApplicationTestBuilder.request(path: String, tenant: String?, token: String?): HttpResponse =
        client.get(path) {
            if (tenant != null) {
                header(TENANT_HEADER, tenant)
            }
            if (token != null) {
                bearerAuth(token)
            }
        }

    @Test
    fun callPassingEveryPluginIsAnswered() = testApplication {
        billingGateway()

        val response: HttpResponse = request("/invoices", "acme", "alice-token")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("invoices of acme for alice", response.bodyAsText())
    }

    @Test
    fun tenantPluginFailuresReachTheirHandlers() = testApplication {
        billingGateway()

        val missing: HttpResponse = request("/invoices", null, "alice-token")
        val unknown: HttpResponse = request("/invoices", "globex", "alice-token")

        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals("tenant header required", missing.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals("no such tenant: globex", unknown.bodyAsText())
    }

    @Test
    fun revokedTokenIsRefusedWhileUnknownTokenIsChallenged() = testApplication {
        billingGateway()

        val revoked: HttpResponse = request("/invoices", "acme", "mallory-token")
        val unknown: HttpResponse = request("/invoices", "acme", "forged-token")

        assertEquals(HttpStatusCode.Forbidden, revoked.status)
        assertEquals("token of mallory was revoked", revoked.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, unknown.status)
        assertEquals("Bearer", unknown.headers["WWW-Authenticate"])
    }

    @Test
    fun responseSideFailureReplacesTheLeakingBody() = testApplication {
        billingGateway()

        val response: HttpResponse = request("/payment-methods", "acme", "alice-token")
        val body: String = response.bodyAsText()

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("response from /payment-methods withheld", body)
        assertFalse(body.contains("4111"))
    }

    private companion object {
        const val TENANT_HEADER: String = "X-Tenant"
        val TENANT: AttributeKey<String> = AttributeKey("tenant")
        val KNOWN_TENANTS: Set<String> = setOf("acme", "initech")
        val ACTIVE_TOKENS: Map<String, String> = mapOf("alice-token" to "alice")
        val REVOKED_TOKENS: Map<String, String> = mapOf("mallory-token" to "mallory")
        val CARD_NUMBER: Regex = Regex("""\d{13,19}""")

        /** Resolves the tenant of every call from its header before routing runs. */
        val TenantResolution: ApplicationPlugin<Unit> = createApplicationPlugin("TenantResolution") {
            onCall { call ->
                val tenant: String = call.request.header(TENANT_HEADER) ?: throw TenantException.Missing()
                if (tenant !in KNOWN_TENANTS) {
                    throw TenantException.Unknown(tenant)
                }
                call.attributes.put(TENANT, tenant)
            }
        }

        /** Refuses to send any text body that still carries an unmasked card number. */
        val CardRedaction: ApplicationPlugin<Unit> = createApplicationPlugin("CardRedaction") {
            onCallRespond { call ->
                transformBody { body ->
                    if (body is String && CARD_NUMBER.containsMatchIn(body)) {
                        throw UnredactedCardNumber(call.request.path())
                    }
                    body
                }
            }
        }
    }
}
