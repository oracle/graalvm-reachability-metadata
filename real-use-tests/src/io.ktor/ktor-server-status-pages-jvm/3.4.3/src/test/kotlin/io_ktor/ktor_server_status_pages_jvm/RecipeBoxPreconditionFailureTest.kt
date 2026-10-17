/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Collections
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Exception handlers for the failures Kotlin's standard library raises, where a
 * recipe box guards its routes with `require`, `check`, and `TODO()`.
 *
 * Failed `require`/`requireNotNull` calls reach the `IllegalArgumentException`
 * handler as a 400, failed `check`/`error` calls reach the `IllegalStateException`
 * handler as a 409, and `TODO()` raises a `NotImplementedError`, an `Error` rather
 * than an `Exception`, which only its own handler answers with a 501.
 */
public class RecipeBoxPreconditionFailureTest {
    /** A recipe and whether a cook has locked it for editing. */
    private data class Recipe(val title: String, val servings: Int, val locked: Boolean)

    /** Installs the recipe box and returns the log of every handler invocation. */
    private fun ApplicationTestBuilder.recipeBox(): MutableList<String> {
        val handled: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val recipes: MutableMap<String, Recipe> = Collections.synchronizedMap(
            mutableMapOf(
                "pancakes" to Recipe("Pancakes", servings = 4, locked = false),
                "risotto" to Recipe("Risotto", servings = 2, locked = true),
            ),
        )
        application {
            install(StatusPages) {
                exception<IllegalArgumentException> { call, cause ->
                    handled += "bad-request"
                    call.respondText("rejected: ${cause.message}", status = HttpStatusCode.BadRequest)
                }
                exception<IllegalStateException> { call, cause ->
                    handled += "conflict"
                    call.respondText("conflict: ${cause.message}", status = HttpStatusCode.Conflict)
                }
                exception<NotImplementedError> { call, _ ->
                    handled += "not-implemented"
                    call.respondText(
                        "${call.request.httpMethod.value} ${call.request.path()} is coming soon",
                        status = HttpStatusCode.NotImplemented,
                    )
                }
                exception<Exception> { call, cause ->
                    handled += "safety-net"
                    call.respondText(
                        "unexpected ${cause::class.simpleName}",
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            }
            routing {
                get("/recipes/{slug}") {
                    val slug: String = call.parameters["slug"]!!
                    val recipe: Recipe = requireNotNull(recipes[slug]) { "no recipe called $slug" }
                    call.respondText("${recipe.title} serves ${recipe.servings}")
                }
                get("/recipes/{slug}/scaled") {
                    val recipe: Recipe = requireNotNull(recipes[call.parameters["slug"]!!]) { "no such recipe" }
                    val servings: Int = call.request.queryParameters["servings"]?.toIntOrNull() ?: 0
                    require(servings in 1..24) { "servings must be between 1 and 24, was $servings" }
                    call.respondText("${recipe.title} scaled to $servings servings")
                }
                post("/recipes/{slug}/edit") {
                    val slug: String = call.parameters["slug"]!!
                    val recipe: Recipe = requireNotNull(recipes[slug]) { "no recipe called $slug" }
                    check(!recipe.locked) { "${recipe.title} is locked by another cook" }
                    recipes[slug] = recipe.copy(locked = true)
                    call.respondText("editing ${recipe.title}")
                }
                delete("/recipes/{slug}") {
                    val slug: String = call.parameters["slug"]!!
                    val removed: Recipe = recipes.remove(slug) ?: error("$slug was already deleted")
                    call.respondText("deleted ${removed.title}")
                }
                get("/recipes/{slug}/nutrition") {
                    TODO("nutrition facts for ${call.parameters["slug"]}")
                }
                get("/recipes/{slug}/rating") {
                    val votes: List<Int> = emptyList()
                    call.respondText("rated ${votes.sum() / votes.size}")
                }
            }
        }
        return handled
    }

    @Test
    fun guardedCallsThatPassTheirPreconditionsAreNotIntercepted() = testApplication {
        val handled: MutableList<String> = recipeBox()

        val read: HttpResponse = client.get("/recipes/pancakes")
        val scaled: HttpResponse = client.get("/recipes/pancakes/scaled?servings=8")
        val edit: HttpResponse = client.post("/recipes/pancakes/edit")

        assertEquals(HttpStatusCode.OK, read.status)
        assertEquals("Pancakes serves 4", read.bodyAsText())
        assertEquals(HttpStatusCode.OK, scaled.status)
        assertEquals("Pancakes scaled to 8 servings", scaled.bodyAsText())
        assertEquals(HttpStatusCode.OK, edit.status)
        assertEquals("editing Pancakes", edit.bodyAsText())
        assertEquals(emptyList<String>(), handled.toList())
    }

    @Test
    fun failedRequirementsAreAnsweredAsBadRequests() = testApplication {
        val handled: MutableList<String> = recipeBox()

        val missing: HttpResponse = client.get("/recipes/goulash")
        val outOfRange: HttpResponse = client.get("/recipes/pancakes/scaled?servings=40")

        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals("rejected: no recipe called goulash", missing.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, outOfRange.status)
        assertEquals("rejected: servings must be between 1 and 24, was 40", outOfRange.bodyAsText())
        assertEquals(listOf("bad-request", "bad-request"), handled.toList())
    }

    @Test
    fun failedStateChecksAreAnsweredAsConflicts() = testApplication {
        val handled: MutableList<String> = recipeBox()

        val firstEdit: HttpResponse = client.post("/recipes/pancakes/edit")
        val secondEdit: HttpResponse = client.post("/recipes/pancakes/edit")
        val firstDelete: HttpResponse = client.delete("/recipes/risotto")
        val secondDelete: HttpResponse = client.delete("/recipes/risotto")

        assertEquals(HttpStatusCode.OK, firstEdit.status)
        assertEquals(HttpStatusCode.Conflict, secondEdit.status)
        assertEquals("conflict: Pancakes is locked by another cook", secondEdit.bodyAsText())
        assertEquals(HttpStatusCode.OK, firstDelete.status)
        assertEquals("deleted Risotto", firstDelete.bodyAsText())
        assertEquals(HttpStatusCode.Conflict, secondDelete.status)
        assertEquals("conflict: risotto was already deleted", secondDelete.bodyAsText())
        assertEquals(listOf("conflict", "conflict"), handled.toList())
    }

    @Test
    fun unfinishedRoutesReachTheNotImplementedHandlerInsteadOfTheSafetyNet() = testApplication {
        val handled: MutableList<String> = recipeBox()

        val nutrition: HttpResponse = client.get("/recipes/pancakes/nutrition")
        val rating: HttpResponse = client.get("/recipes/pancakes/rating")

        assertEquals(HttpStatusCode.NotImplemented, nutrition.status)
        assertEquals("GET /recipes/pancakes/nutrition is coming soon", nutrition.bodyAsText())
        assertEquals(HttpStatusCode.InternalServerError, rating.status)
        assertEquals("unexpected ArithmeticException", rating.bodyAsText())
        assertEquals(listOf("not-implemented", "safety-net"), handled.toList())
    }
}
