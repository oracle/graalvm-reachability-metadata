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
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.html.Template
import io.ktor.server.html.respondHtml
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Collections
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.p
import kotlinx.html.title
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exception handlers for failures raised while a response body is rendered, where
 * a newsroom's HTML templates and JSON feed serializers refuse to publish a story.
 *
 * An HTML template that throws half way through a page and a kotlinx serializer
 * that throws while the feed is being encoded both reach their `StatusPages`
 * handler before anything is sent, so the reader gets the error page and never a
 * fragment of the story that failed to render.
 */
public class NewsroomRenderingFailureTest {
    /** A story as the newsroom's content store holds it. */
    @Serializable(with = StorySerializer::class)
    public class Story(
        val slug: String,
        val headline: String,
        val byline: String?,
        val embargoedUntil: String?,
    )

    /** The wire form of a story that the public feed publishes. */
    @Serializable
    private class PublishedStory(val slug: String, val headline: String, val byline: String)

    /** The public JSON feed of the newsroom's front page. */
    @Serializable
    private class FrontPageFeed(val edition: String, val stories: List<Story>)

    /** A story's page was rendered before an editor assigned its byline. */
    private class MissingBylineException(val slug: String) : RuntimeException("story $slug has no byline")

    /** A story under embargo was about to leave the newsroom. */
    private class EmbargoBreachException(val slug: String, val until: String) :
        RuntimeException("story $slug is embargoed until $until")

    /** Publishes a story through its [PublishedStory] surrogate, refusing what is not cleared for print. */
    public object StorySerializer : KSerializer<Story> {
        override val descriptor: SerialDescriptor = PublishedStory.serializer().descriptor

        override fun serialize(encoder: Encoder, value: Story) {
            val until: String? = value.embargoedUntil
            if (until != null) {
                throw EmbargoBreachException(value.slug, until)
            }
            val byline: String = value.byline ?: throw MissingBylineException(value.slug)
            encoder.encodeSerializableValue(
                PublishedStory.serializer(),
                PublishedStory(value.slug, value.headline, byline),
            )
        }

        override fun deserialize(decoder: Decoder): Story {
            val published: PublishedStory = decoder.decodeSerializableValue(PublishedStory.serializer())
            return Story(published.slug, published.headline, published.byline, null)
        }
    }

    /** The article page layout every story is rendered with. */
    private class ArticlePage(private val story: Story) : Template<HTML> {
        override fun HTML.apply() {
            head { title { +"Daily Ledger - ${story.headline}" } }
            body {
                h1 { +story.headline }
                p { +"By ${story.byline ?: throw MissingBylineException(story.slug)}" }
                p { +"Full text of ${story.slug}." }
            }
        }
    }

    private val stories: Map<String, Story> = linkedMapOf(
        "harbour-reopens" to Story("harbour-reopens", "Harbour reopens after storm", "A. Lindqvist", null),
        "council-budget" to Story("council-budget", "Council passes budget", null, null),
        "merger-talks" to Story("merger-talks", "Mill and port in merger talks", "R. Okafor", "2026-11-01T06:00Z"),
    )

    /** The newsroom site, with the slug of every story whose rendering failed recorded in [failures]. */
    private fun ApplicationTestBuilder.newsroom(failures: MutableList<String>) {
        application {
            install(ContentNegotiation) {
                json(FEED_JSON)
            }
            install(StatusPages) {
                exception<MissingBylineException> { call, cause ->
                    failures += "byline:${cause.slug}"
                    call.respondHtml(HttpStatusCode.InternalServerError) {
                        head { title { +"Daily Ledger - page unavailable" } }
                        body {
                            h1 { +"This page is being edited" }
                            p { +"Story ${cause.slug} will be back shortly." }
                        }
                    }
                }
                exception<EmbargoBreachException> { call, cause ->
                    failures += "embargo:${cause.slug}"
                    call.respond(UNAVAILABLE_FOR_LEGAL_REASONS, "embargoed until ${cause.until}")
                }
            }
            routing {
                get("/stories/{slug}") {
                    val story: Story = stories.getValue(call.parameters["slug"]!!)
                    call.respondHtmlTemplate(ArticlePage(story)) {}
                }
                get("/feed/{slugs}") {
                    val selected: List<Story> = call.parameters["slugs"]!!.split(",").map { slug -> stories.getValue(slug) }
                    call.respond(FrontPageFeed("morning", selected))
                }
            }
        }
    }

    @Test
    fun storyWithBylineRendersItsPage() = testApplication {
        val failures: MutableList<String> = Collections.synchronizedList(mutableListOf())
        newsroom(failures)

        val response: HttpResponse = client.get("/stories/harbour-reopens")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Html))
        val page: String = response.bodyAsText()
        assertTrue(page.contains("<h1>Harbour reopens after storm</h1>"), page)
        assertTrue(page.contains("By A. Lindqvist"), page)
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun templateFailingMidPageIsReplacedByErrorPageWithoutFragment() = testApplication {
        val failures: MutableList<String> = Collections.synchronizedList(mutableListOf())
        newsroom(failures)

        val response: HttpResponse = client.get("/stories/council-budget")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Html))
        val page: String = response.bodyAsText()
        assertTrue(page.contains("<h1>This page is being edited</h1>"), page)
        assertTrue(page.contains("Story council-budget will be back shortly."), page)
        assertFalse(page.contains("Council passes budget"), page)
        assertEquals(listOf("byline:council-budget"), failures)
    }

    @Test
    fun feedOfClearedStoriesIsSerialized() = testApplication {
        val failures: MutableList<String> = Collections.synchronizedList(mutableListOf())
        newsroom(failures)

        val response: HttpResponse = client.get("/feed/harbour-reopens")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        val feed = FEED_JSON.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("morning", feed.getValue("edition").jsonPrimitive.content)
        val story = feed.getValue("stories").jsonArray.single().jsonObject
        assertEquals("harbour-reopens", story.getValue("slug").jsonPrimitive.content)
        assertEquals("A. Lindqvist", story.getValue("byline").jsonPrimitive.content)
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun serializerRefusingEmbargoedStoryAbortsWholeFeed() = testApplication {
        val failures: MutableList<String> = Collections.synchronizedList(mutableListOf())
        newsroom(failures)

        val response: HttpResponse = client.get("/feed/harbour-reopens,merger-talks")

        assertEquals(UNAVAILABLE_FOR_LEGAL_REASONS, response.status)
        val body: String = response.bodyAsText()
        assertEquals("embargoed until 2026-11-01T06:00Z", body)
        assertFalse(body.contains("harbour-reopens"), body)
        assertEquals(listOf("embargo:merger-talks"), failures)
    }

    @Test
    fun serializerAndTemplateShareTheBylineHandler() = testApplication {
        val failures: MutableList<String> = Collections.synchronizedList(mutableListOf())
        newsroom(failures)

        val response: HttpResponse = client.get("/feed/council-budget")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Html))
        assertTrue(response.bodyAsText().contains("Story council-budget will be back shortly."))
        assertEquals(listOf("byline:council-budget"), failures)
    }

    private companion object {
        val FEED_JSON: Json = Json { encodeDefaults = true }

        /** Ktor names no constant for the status that RFC 7725 defines for legally withheld content. */
        val UNAVAILABLE_FOR_LEGAL_REASONS: HttpStatusCode = HttpStatusCode(451, "Unavailable For Legal Reasons")
    }
}
