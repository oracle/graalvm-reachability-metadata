/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.parameters
import io.ktor.resources.Resource
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.resources.Resources
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.href
import io.ktor.server.resources.post
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.runBlocking
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The CIO engine's type-safe routing: serializable resource classes that decode nested path
 * segments and repeated query parameters, generate links back into the same routes, and
 * render the result as server-side HTML pages.
 *
 * A community recipe book runs on an embedded CIO server. Cooks browse a catalog filtered by
 * cuisine and tags and paged through links the server builds from its own resource classes,
 * open a recipe page that links each step, submit a new recipe from an HTML form and follow
 * the generated redirect, and delete a recipe. The test checks the rendered pages, the links
 * and redirect targets the server derives from resource instances, and that malformed typed
 * parameters and unknown recipes surface as the status codes a browser would see.
 */
class RecipeBookTypedRoutingTest {

    @Serializable
    @Resource("/recipes")
    class Recipes(val cuisine: String? = null, val tag: List<String> = emptyList(), val page: Int? = null) {

        @Serializable
        @Resource("new")
        class New(val parent: Recipes = Recipes())

        @Serializable
        @Resource("{slug}")
        class BySlug(val parent: Recipes = Recipes(), val slug: String) {

            @Serializable
            @Resource("steps/{number}")
            class Step(val parent: BySlug, val number: Int)
        }
    }

    data class Recipe(val slug: String, val name: String, val cuisine: String, val tags: Set<String>, val steps: List<String>)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val book: MutableMap<String, Recipe> = ConcurrentHashMap()

    @BeforeEach
    fun start() {
        listOf(
            Recipe("shakshuka", "Shakshuka", "levantine", setOf("vegetarian", "quick"), listOf("Fry onions", "Add tomatoes", "Poach eggs")),
            Recipe("hummus", "Hummus", "levantine", setOf("vegetarian", "vegan"), listOf("Soak chickpeas", "Blend with tahini")),
            Recipe("falafel", "Falafel", "levantine", setOf("vegetarian", "vegan", "quick"), listOf("Grind chickpeas", "Fry balls")),
            Recipe("mujaddara", "Mujaddara", "levantine", setOf("vegetarian", "vegan", "quick"), listOf("Cook lentils", "Add rice")),
            Recipe("ramen", "Ramen", "japanese", setOf("quick"), listOf("Boil noodles", "Pour broth")),
        ).forEach { recipe: Recipe -> book[recipe.slug] = recipe }

        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(Resources)
            routing {
                get<Recipes> { query: Recipes ->
                    val matching: List<Recipe> = book.values
                        .filter { recipe: Recipe -> query.cuisine == null || recipe.cuisine == query.cuisine }
                        .filter { recipe: Recipe -> recipe.tags.containsAll(query.tag) }
                        .sortedBy { recipe: Recipe -> recipe.slug }
                    val page: Int = query.page ?: 1
                    val shown: List<Recipe> = matching.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE)
                    val hasNext: Boolean = matching.size > page * PAGE_SIZE
                    val application = call.application
                    call.respondHtml {
                        head { title { +"Recipes, page $page" } }
                        body {
                            h1 { +"${matching.size} recipes" }
                            ul {
                                shown.forEach { recipe: Recipe ->
                                    li { a(href = application.href(Recipes.BySlug(slug = recipe.slug))) { +recipe.name } }
                                }
                            }
                            if (hasNext) {
                                a(href = application.href(Recipes(query.cuisine, query.tag, page + 1))) { +"Next" }
                            }
                            form(action = application.href(Recipes.New()), method = FormMethod.post) { }
                        }
                    }
                }
                get<Recipes.BySlug> { target: Recipes.BySlug ->
                    val recipe: Recipe = book[target.slug] ?: return@get call.respond(HttpStatusCode.NotFound)
                    val application = call.application
                    call.respondHtml {
                        head { title { +recipe.name } }
                        body {
                            h1 { +recipe.name }
                            ol {
                                recipe.steps.indices.forEach { index: Int ->
                                    li { a(href = application.href(Recipes.BySlug.Step(target, index + 1))) { +recipe.steps[index] } }
                                }
                            }
                            a(href = application.href(Recipes(cuisine = recipe.cuisine))) { +"More ${recipe.cuisine}" }
                        }
                    }
                }
                get<Recipes.BySlug.Step> { step: Recipes.BySlug.Step ->
                    val recipe: Recipe? = book[step.parent.slug]
                    val text: String? = recipe?.steps?.getOrNull(step.number - 1)
                    if (text == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respondText("Step ${step.number} of ${recipe.steps.size}: $text")
                    }
                }
                post<Recipes.New> { _: Recipes.New ->
                    val form: Parameters = call.receiveParameters()
                    val recipe = Recipe(
                        slug = form.getOrFail("name").lowercase().replace(' ', '-'),
                        name = form.getOrFail("name"),
                        cuisine = form.getOrFail("cuisine"),
                        tags = form.getAll("tag").orEmpty().toSet(),
                        steps = form.getAll("step").orEmpty(),
                    )
                    book[recipe.slug] = recipe
                    call.response.header(HttpHeaders.Location, call.application.href(Recipes.BySlug(slug = recipe.slug)))
                    call.respond(HttpStatusCode.SeeOther)
                }
                delete<Recipes.BySlug> { target: Recipes.BySlug ->
                    val removed: Recipe? = book.remove(target.slug)
                    call.respond(if (removed == null) HttpStatusCode.NotFound else HttpStatusCode.NoContent)
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
    fun cooksBrowseSubmitAndRemoveRecipesThroughTypedRoutes() {
        val client = HttpClient(ClientCIO) {
            followRedirects = false
        }

        client.use {
            runBlocking {
                val base = "http://127.0.0.1:$port"

                val firstPage: HttpResponse = it.get("$base/recipes?cuisine=levantine&tag=vegetarian&tag=quick")
                assertEquals(HttpStatusCode.OK, firstPage.status)
                assertTrue(firstPage.headers[HttpHeaders.ContentType]!!.startsWith(ContentType.Text.Html.toString()))
                val firstHtml: String = firstPage.bodyAsText()
                assertEquals("Recipes, page 1", firstMatch(TITLE, firstHtml))
                assertEquals("3 recipes", firstMatch(HEADING, firstHtml))
                assertEquals(
                    listOf(
                        "/recipes/falafel",
                        "/recipes/mujaddara",
                        "/recipes?cuisine=levantine&tag=vegetarian&tag=quick&page=2",
                    ),
                    links(firstHtml),
                )
                assertEquals("/recipes/new", firstMatch(FORM_ACTION, firstHtml))

                val nextPage: String = it.get(base + links(firstHtml).last()).bodyAsText()
                assertEquals("Recipes, page 2", firstMatch(TITLE, nextPage))
                assertEquals(listOf("/recipes/shakshuka"), links(nextPage))

                val recipePage: String = it.get(base + links(nextPage).single()).bodyAsText()
                assertEquals("Shakshuka", firstMatch(HEADING, recipePage))
                assertEquals(
                    listOf(
                        "/recipes/shakshuka/steps/1",
                        "/recipes/shakshuka/steps/2",
                        "/recipes/shakshuka/steps/3",
                        "/recipes?cuisine=levantine",
                    ),
                    links(recipePage),
                )
                assertEquals("Step 3 of 3: Poach eggs", it.get("$base/recipes/shakshuka/steps/3").bodyAsText())
                assertEquals(HttpStatusCode.NotFound, it.get("$base/recipes/shakshuka/steps/4").status)
                assertEquals(HttpStatusCode.BadRequest, it.get("$base/recipes/shakshuka/steps/last").status)
                assertEquals(HttpStatusCode.BadRequest, it.get("$base/recipes?page=two").status)
                assertEquals(HttpStatusCode.NotFound, it.get("$base/recipes/goulash").status)

                val submitted: HttpResponse = it.submitForm(
                    url = base + firstMatch(FORM_ACTION, firstHtml),
                    formParameters = parameters {
                        append("name", "Miso Soup")
                        append("cuisine", "japanese")
                        appendAll("tag", listOf("quick", "vegan"))
                        appendAll("step", listOf("Heat dashi", "Whisk in miso"))
                    },
                )
                assertEquals(HttpStatusCode.SeeOther, submitted.status)
                val location: String = submitted.headers[HttpHeaders.Location]!!
                assertEquals("/recipes/miso-soup", location)
                assertEquals("Step 2 of 2: Whisk in miso", it.get("$base$location/steps/2").bodyAsText())

                val japaneseQuick: String = it.get("$base/recipes?cuisine=japanese&tag=quick").bodyAsText()
                assertEquals("2 recipes", firstMatch(HEADING, japaneseQuick))
                assertEquals(listOf("/recipes/miso-soup", "/recipes/ramen"), links(japaneseQuick))

                assertEquals(HttpStatusCode.NoContent, it.delete("$base/recipes/ramen").status)
                assertEquals(HttpStatusCode.NotFound, it.delete("$base/recipes/ramen").status)
                assertEquals(HttpStatusCode.NotFound, it.get("$base/recipes/ramen").status)
            }
        }

        assertEquals(setOf("falafel", "hummus", "miso-soup", "mujaddara", "shakshuka"), book.keys.toSet())
        assertEquals(setOf("quick", "vegan"), book.getValue("miso-soup").tags)
    }

    private fun links(html: String): List<String> =
        LINK.findAll(html).map { match: MatchResult -> match.groupValues[1].replace("&amp;", "&") }.toList()

    private fun firstMatch(pattern: Regex, html: String): String = pattern.find(html)!!.groupValues[1].trim()

    private companion object {
        const val PAGE_SIZE: Int = 2
        val LINK: Regex = Regex("""<a href="([^"]+)"""")
        val TITLE: Regex = Regex("""<title>([^<]+)</title>""")
        val HEADING: Regex = Regex("""<h1>([^<]+)</h1>""")
        val FORM_ACTION: Regex = Regex("""<form action="([^"]+)"""")
    }
}
