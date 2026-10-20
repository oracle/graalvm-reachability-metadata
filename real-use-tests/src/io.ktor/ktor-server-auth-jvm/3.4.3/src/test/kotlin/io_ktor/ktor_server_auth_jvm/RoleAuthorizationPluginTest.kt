/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The `AuthenticationChecked` hook driving an application's role-based authorization plugin.
 *
 * A clinic back office authenticates employees with HTTP Basic and attaches their roles to the
 * principal. A route-scoped plugin installed inside the authenticated routes runs on
 * `AuthenticationChecked`, after the principal is known and before the handler, and answers
 * `403 Forbidden` when the employee lacks a role the route demands. Anonymous callers never
 * reach the plugin: authentication challenges them first.
 */
class RoleAuthorizationPluginTest {

    /** An authenticated clinic employee and the roles the directory grants them. */
    data class Employee(val name: String, val roles: Set<String>)

    /** Configuration of [RoleAuthorization]: the employee needs at least one of [anyOf]. */
    class RoleAuthorizationConfig {
        var anyOf: Set<String> = emptySet()
    }

    private val directory: Map<String, Pair<String, Employee>> = mapOf(
        "amara" to ("triage-42" to Employee("amara", setOf("nurse"))),
        "bastian" to ("ledger-77" to Employee("bastian", setOf("billing"))),
        "chiara" to ("root-19" to Employee("chiara", setOf("admin", "billing"))),
    )

    /** Every decision the plugin made, in order, as `route:employee:verdict`. */
    private val decisions: MutableList<String> = mutableListOf()

    private val roleAuthorization: RouteScopedPlugin<RoleAuthorizationConfig> =
        createRouteScopedPlugin("RoleAuthorization", ::RoleAuthorizationConfig) {
            val required: Set<String> = pluginConfig.anyOf
            on(AuthenticationChecked) { call ->
                val employee: Employee = call.principal<Employee>()!!
                val allowed: Boolean = employee.roles.any { it in required }
                decisions += "${call.request.local.uri}:${employee.name}:${if (allowed) "allow" else "deny"}"
                if (!allowed) {
                    call.respondText(
                        "${employee.name} needs one of ${required.sorted()}",
                        status = HttpStatusCode.Forbidden,
                    )
                }
            }
        }

    private fun Application.backOfficeModule() {
        install(Authentication) {
            basic("employees") {
                realm = "Clinic back office"
                validate { credentials: UserPasswordCredential ->
                    directory[credentials.name]
                        ?.takeIf { (password, _) -> password == credentials.password }
                        ?.second
                }
            }
        }
        routing {
            authenticate("employees") {
                get("/schedule") {
                    call.respondText("schedule for ${call.principal<Employee>()!!.name}")
                }
                route("/invoices") {
                    install(roleAuthorization) { anyOf = setOf("billing") }
                    get {
                        call.respondText("invoices opened by ${call.principal<Employee>()!!.name}")
                    }
                }
                route("/staff") {
                    install(roleAuthorization) { anyOf = setOf("admin") }
                    get {
                        call.respondText("staff roster opened by ${call.principal<Employee>()!!.name}")
                    }
                }
            }
        }
    }

    @Test
    fun rolesDecideWhichAuthenticatedEmployeesReachEachRoute() = testApplication {
        application { backOfficeModule() }

        val nurseSchedule: HttpResponse = client.get("/schedule") { basicAuth("amara", "triage-42") }
        assertEquals(HttpStatusCode.OK, nurseSchedule.status)
        assertEquals("schedule for amara", nurseSchedule.bodyAsText())

        val nurseInvoices: HttpResponse = client.get("/invoices") { basicAuth("amara", "triage-42") }
        assertEquals(HttpStatusCode.Forbidden, nurseInvoices.status)
        assertEquals("amara needs one of [billing]", nurseInvoices.bodyAsText())
        assertNull(nurseInvoices.headers[HttpHeaders.WWWAuthenticate], "a forbidden caller is not re-challenged")

        val billingInvoices: HttpResponse = client.get("/invoices") { basicAuth("bastian", "ledger-77") }
        assertEquals(HttpStatusCode.OK, billingInvoices.status)
        assertEquals("invoices opened by bastian", billingInvoices.bodyAsText())

        val billingStaff: HttpResponse = client.get("/staff") { basicAuth("bastian", "ledger-77") }
        assertEquals(HttpStatusCode.Forbidden, billingStaff.status)
        assertEquals("bastian needs one of [admin]", billingStaff.bodyAsText())

        val adminStaff: HttpResponse = client.get("/staff") { basicAuth("chiara", "root-19") }
        assertEquals(HttpStatusCode.OK, adminStaff.status)
        assertEquals("staff roster opened by chiara", adminStaff.bodyAsText())

        val adminInvoices: HttpResponse = client.get("/invoices") { basicAuth("chiara", "root-19") }
        assertEquals("invoices opened by chiara", adminInvoices.bodyAsText())

        val anonymous: HttpResponse = client.get("/staff")
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertEquals("Basic realm=\"Clinic back office\", charset=UTF-8", anonymous.headers[HttpHeaders.WWWAuthenticate])

        val wrongPassword: HttpResponse = client.get("/invoices") { basicAuth("bastian", "guess") }
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)

        assertEquals(
            listOf(
                "/invoices:amara:deny",
                "/invoices:bastian:allow",
                "/staff:bastian:deny",
                "/staff:chiara:allow",
                "/invoices:chiara:allow",
            ),
            decisions,
            "the plugin only sees authenticated employees on the routes that install it",
        )
    }
}
