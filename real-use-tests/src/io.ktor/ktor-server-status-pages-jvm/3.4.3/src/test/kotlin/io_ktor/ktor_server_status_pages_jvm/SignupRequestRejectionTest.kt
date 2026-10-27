/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.util.getOrFail
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exception handlers for the framework's own request-rejection exceptions, where
 * a signup API turns every way Ktor refuses a request into one problem document.
 *
 * `RequestValidation` failures, unconvertible and missing parameters, malformed
 * JSON bodies, and bodies no converter accepts each reach their own handler, the
 * first three reading the exception's properties, while accepted signups pass
 * through untouched.
 */
public class SignupRequestRejectionTest {
    /** A signup form as clients post it. */
    @Serializable
    data class Signup(val email: String, val nickname: String, val age: Int)

    /** A stored account as the API reports it. */
    @Serializable
    data class Account(val id: Int, val email: String, val nickname: String)

    /** The problem document every rejected request answers with. */
    @Serializable
    data class Problem(val type: String, val detail: String, val reasons: List<String> = emptyList())

    /** The signup API with a fresh in-memory account store. */
    private fun ApplicationTestBuilder.signupApi() {
        val accounts: MutableMap<Int, Account> = mutableMapOf(1 to Account(1, "ada@example.org", "ada"))
        application {
            install(ContentNegotiation) {
                json()
            }
            install(RequestValidation) {
                validate<Signup> { signup ->
                    val reasons: List<String> = buildList {
                        if ("@" !in signup.email) add("email must contain @")
                        if (signup.nickname.length < 3) add("nickname must have at least 3 characters")
                        if (signup.age < 16) add("age must be at least 16")
                    }
                    if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
                }
            }
            install(StatusPages) {
                exception<RequestValidationException> { call, cause ->
                    val rejected: Signup = cause.value as Signup
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        Problem("invalid-signup", "Signup for ${rejected.email} was rejected", cause.reasons),
                    )
                }
                exception<ParameterConversionException> { call, cause ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        Problem("bad-parameter", "${cause.parameterName} must be ${cause.type}"),
                    )
                }
                exception<MissingRequestParameterException> { call, cause ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        Problem("missing-parameter", "${cause.parameterName} is required"),
                    )
                }
                exception<BadRequestException> { call, _ ->
                    call.respond(HttpStatusCode.BadRequest, Problem("malformed-body", "Request body is not a signup"))
                }
                // A body in a media type no converter reads cannot become a Signup at all.
                exception<ContentTransformationException> { call, _ ->
                    call.respond(
                        HttpStatusCode.UnsupportedMediaType,
                        Problem("unsupported-media-type", "Signups must be sent as application/json"),
                    )
                }
            }
            routing {
                post("/signups") {
                    val signup: Signup = call.receive()
                    val account = Account(accounts.size + 1, signup.email, signup.nickname)
                    accounts[account.id] = account
                    call.respond(HttpStatusCode.Created, account)
                }
                get("/accounts/{id}") {
                    val id: Int = call.parameters.getOrFail<Int>("id")
                    call.respond(accounts.getValue(id))
                }
                get("/accounts") {
                    val nickname: String = call.request.queryParameters.getOrFail("nickname")
                    call.respond(accounts.values.filter { it.nickname == nickname })
                }
            }
        }
    }

    private suspend fun HttpResponse.problem(): Problem {
        assertTrue(contentType()!!.match(ContentType.Application.Json))
        return Json.decodeFromString(bodyAsText())
    }

    @Test
    fun acceptedSignupIsCreated() = testApplication {
        signupApi()

        val response: HttpResponse = client.post("/signups") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"grace@example.org","nickname":"grace","age":36}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(
            Account(2, "grace@example.org", "grace"),
            Json.decodeFromString<Account>(response.bodyAsText()),
        )
    }

    @Test
    fun failedValidationListsEveryReason() = testApplication {
        signupApi()

        val response: HttpResponse = client.post("/signups") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"linus.example.org","nickname":"li","age":12}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            Problem(
                "invalid-signup",
                "Signup for linus.example.org was rejected",
                listOf(
                    "email must contain @",
                    "nickname must have at least 3 characters",
                    "age must be at least 16",
                ),
            ),
            response.problem(),
        )
    }

    @Test
    fun badAndMissingParametersNameTheParameter() = testApplication {
        signupApi()

        val notANumber: HttpResponse = client.get("/accounts/first")
        val noNickname: HttpResponse = client.get("/accounts")
        val found: HttpResponse = client.get("/accounts?nickname=ada")

        assertEquals(HttpStatusCode.BadRequest, notANumber.status)
        assertEquals(Problem("bad-parameter", "id must be Int"), notANumber.problem())
        assertEquals(HttpStatusCode.BadRequest, noNickname.status)
        assertEquals(Problem("missing-parameter", "nickname is required"), noNickname.problem())
        assertEquals(HttpStatusCode.OK, found.status)
        assertEquals(
            listOf(Account(1, "ada@example.org", "ada")),
            Json.decodeFromString<List<Account>>(found.bodyAsText()),
        )
    }

    @Test
    fun malformedAndUnsupportedBodiesAreRejected() = testApplication {
        signupApi()

        val truncated: HttpResponse = client.post("/signups") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"grace@example.org","nickname":""")
        }
        val plainText: HttpResponse = client.post("/signups") {
            contentType(ContentType.Text.Plain)
            setBody("grace@example.org grace 36")
        }

        assertEquals(HttpStatusCode.BadRequest, truncated.status)
        assertEquals(Problem("malformed-body", "Request body is not a signup"), truncated.problem())
        assertEquals(HttpStatusCode.UnsupportedMediaType, plainText.status)
        assertEquals(
            Problem("unsupported-media-type", "Signups must be sent as application/json"),
            plainText.problem(),
        )
    }
}
