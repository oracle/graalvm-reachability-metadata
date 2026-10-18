/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.toHttpDate
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.CompressedFileType
import io.ktor.server.http.content.ETagProvider
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.routing
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The CIO engine's static file serving: a directory published with `staticFiles`, content
 * types inferred from file extensions or chosen per file, extensionless page URLs, an index
 * file and a single-page-app default, pre-compressed gzip siblings, excluded drafts, a
 * fallback that redirects legacy URLs, per-file response headers, strong SHA-256 entity tags
 * with conditional GET, fixed last-modified dates, and HEAD responses.
 *
 * A national park publishes its trail guide as a static site. The guide pages, stylesheet,
 * trail map, and GPS tracks are files in a site directory; drafts sit beside them but must
 * never be served, and old `.htm` links from the printed brochure must keep working. A
 * ranger tablet app is a single-page app whose deep links all load the same shell.
 */
class TrailGuideStaticContentTest {

    @TempDir
    lateinit var workspace: File

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var guideRoot: File
    private lateinit var tabletRoot: File
    private var port: Int = 0

    @BeforeEach
    fun start() {
        guideRoot = File(workspace, "guide")
        tabletRoot = File(workspace, "tablet")
        writeSite()

        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ConditionalHeaders)
            routing {
                staticFiles("/guide", guideRoot) {
                    extensions("html")
                    preCompressed(CompressedFileType.GZIP)
                    enableAutoHeadResponse()
                    etag(ETagProvider.StrongSha256)
                    lastModified { _: File -> PUBLISHED }
                    exclude { file: File -> file.path.contains("${File.separator}drafts${File.separator}") }
                    contentType { file: File ->
                        if (file.extension == "gpx") ContentType("application", "gpx+xml") else null
                    }
                    modify { file: File, call ->
                        call.response.header(SERVED_FROM_HEADER, file.relativeTo(guideRoot).invariantSeparatorsPath)
                    }
                    fallback { requestedPath: String, call ->
                        if (requestedPath.endsWith(".htm")) {
                            call.respondRedirect("/guide/${requestedPath.removeSuffix(".htm")}", permanent = true)
                        }
                    }
                }
                staticFiles("/tablet", tabletRoot) {
                    default("shell.html")
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
    fun visitorsAndRangersBrowseTheTrailGuide() {
        HttpClient(ClientCIO) {
            followRedirects = false
        }.use { client: HttpClient ->
            runBlocking {
                val base = "http://127.0.0.1:$port"

                val home: HttpResponse = client.get("$base/guide")
                assertEquals(HttpStatusCode.OK, home.status)
                assertEquals(INDEX_HTML, home.bodyAsText())
                assertEquals(ContentType.Text.Html, home.contentTypeWithoutParameters())
                assertEquals("index.html", home.headers[SERVED_FROM_HEADER])

                val trail: HttpResponse = client.get("$base/guide/trails/eagle-ridge")
                assertEquals(HttpStatusCode.OK, trail.status)
                assertEquals(EAGLE_RIDGE_HTML, trail.bodyAsText())
                assertEquals("trails/eagle-ridge.html", trail.headers[SERVED_FROM_HEADER])
                assertEquals(PUBLISHED.toHttpDate(), trail.headers[HttpHeaders.LastModified])

                val map: HttpResponse = client.get("$base/guide/maps/overview.svg")
                assertEquals(HttpStatusCode.OK, map.status)
                assertEquals(ContentType.Image.SVG, map.contentTypeWithoutParameters())
                assertEquals(OVERVIEW_SVG, map.bodyAsText())

                val track: HttpResponse = client.get("$base/guide/tracks/eagle-ridge.gpx")
                assertEquals(HttpStatusCode.OK, track.status)
                assertEquals(ContentType("application", "gpx+xml"), track.contentTypeWithoutParameters())
                assertEquals(EAGLE_RIDGE_GPX, track.bodyAsText())

                val zipped: HttpResponse = client.get("$base/guide/css/site.css") {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                }
                assertEquals(HttpStatusCode.OK, zipped.status)
                assertEquals("gzip", zipped.headers[HttpHeaders.ContentEncoding])
                assertEquals(ContentType.Text.CSS, zipped.contentTypeWithoutParameters())
                assertEquals(PRECOMPRESSED_CSS, gunzip(zipped.bodyAsBytes()))

                val plainCss: HttpResponse = client.get("$base/guide/css/site.css")
                assertEquals(HttpStatusCode.OK, plainCss.status)
                assertNull(plainCss.headers[HttpHeaders.ContentEncoding])
                assertEquals(SITE_CSS, plainCss.bodyAsText())
                assertEquals("css/site.css", plainCss.headers[SERVED_FROM_HEADER])

                val etag: String = plainCss.headers[HttpHeaders.ETag]!!
                val again: HttpResponse = client.get("$base/guide/css/site.css")
                assertEquals(etag, again.headers[HttpHeaders.ETag])
                val revalidated: HttpResponse = client.get("$base/guide/css/site.css") {
                    header(HttpHeaders.IfNoneMatch, etag)
                }
                assertEquals(HttpStatusCode.NotModified, revalidated.status)
                assertArrayEquals(ByteArray(0), revalidated.bodyAsBytes())
                val trailEtag: String? = trail.headers[HttpHeaders.ETag]
                assertNotNull(trailEtag)
                assertTrue(trailEtag != etag, "distinct files carry distinct entity tags")

                val headOnly: HttpResponse = client.head("$base/guide/trails/eagle-ridge")
                assertEquals(HttpStatusCode.OK, headOnly.status)
                assertEquals(EAGLE_RIDGE_HTML.toByteArray().size.toString(), headOnly.headers[HttpHeaders.ContentLength])
                assertArrayEquals(ByteArray(0), headOnly.bodyAsBytes())

                val draft: HttpResponse = client.get("$base/guide/drafts/closed-trail.html")
                assertEquals(HttpStatusCode.Forbidden, draft.status)
                assertTrue(!draft.bodyAsText().contains("bridge washed out"))

                val legacy: HttpResponse = client.get("$base/guide/trails/eagle-ridge.htm")
                assertEquals(HttpStatusCode.MovedPermanently, legacy.status)
                assertEquals("/guide/trails/eagle-ridge", legacy.headers[HttpHeaders.Location])

                val missing: HttpResponse = client.get("$base/guide/trails/no-such-trail")
                assertEquals(HttpStatusCode.NotFound, missing.status)

                val escape: HttpResponse = client.get("$base/guide/..%2Franger-roster.txt")
                assertEquals(HttpStatusCode.NotFound, escape.status)
                assertTrue(!escape.bodyAsText().contains("night shift"))

                val shell: HttpResponse = client.get("$base/tablet")
                assertEquals(HttpStatusCode.OK, shell.status)
                assertEquals(TABLET_SHELL, shell.bodyAsText())
                val deepLink: HttpResponse = client.get("$base/tablet/incidents/2026/417")
                assertEquals(HttpStatusCode.OK, deepLink.status)
                assertEquals(TABLET_SHELL, deepLink.bodyAsText())
                val bundle: HttpResponse = client.get("$base/tablet/app.js")
                assertEquals(HttpStatusCode.OK, bundle.status)
                assertEquals(ContentType("text", "javascript"), bundle.contentTypeWithoutParameters())
                assertEquals(TABLET_JS, bundle.bodyAsText())
            }
        }
    }

    private fun writeSite() {
        write(guideRoot, "index.html", INDEX_HTML)
        write(guideRoot, "trails/eagle-ridge.html", EAGLE_RIDGE_HTML)
        write(guideRoot, "maps/overview.svg", OVERVIEW_SVG)
        write(guideRoot, "tracks/eagle-ridge.gpx", EAGLE_RIDGE_GPX)
        write(guideRoot, "css/site.css", SITE_CSS)
        File(guideRoot, "css/site.css.gz").writeBytes(gzip(PRECOMPRESSED_CSS))
        write(guideRoot, "drafts/closed-trail.html", "<p>Bear Creek: bridge washed out</p>")
        write(workspace, "ranger-roster.txt", "night shift: Okafor, Lindqvist")
        write(tabletRoot, "shell.html", TABLET_SHELL)
        write(tabletRoot, "app.js", TABLET_JS)
    }

    private fun write(root: File, relativePath: String, text: String) {
        val file = File(root, relativePath)
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    private fun HttpResponse.contentTypeWithoutParameters(): ContentType? =
        headers[HttpHeaders.ContentType]?.let { ContentType.parse(it).withoutParameters() }

    private fun gzip(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(text.toByteArray()) }
        return bytes.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes().decodeToString() }

    private companion object {
        const val SERVED_FROM_HEADER: String = "X-Served-From"
        val PUBLISHED: GMTDate = GMTDate(1_767_225_600_000L)
        const val INDEX_HTML: String = "<h1>Pine Valley Trail Guide</h1>"
        const val EAGLE_RIDGE_HTML: String = "<h1>Eagle Ridge</h1><p>7.4 km, 610 m ascent</p>"
        const val OVERVIEW_SVG: String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0L10 10\"/></svg>"
        const val EAGLE_RIDGE_GPX: String = "<gpx version=\"1.1\"><trk><name>Eagle Ridge</name></trk></gpx>"
        const val SITE_CSS: String = "body { font-family: serif; }"
        const val PRECOMPRESSED_CSS: String = "body{font-family:serif}"
        const val TABLET_SHELL: String = "<div id=\"ranger-app\"></div><script src=\"/tablet/app.js\"></script>"
        const val TABLET_JS: String = "document.getElementById('ranger-app').textContent = 'ready';"
    }
}
