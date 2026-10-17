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
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.statusFile
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Status pages served from static error files with `statusFile`, where a help
 * center answers error statuses with the pages an operator's theme ships.
 *
 * The theme is a directory of files exposed through the application's class
 * loader: each status maps to a file named after its code, a second pattern
 * serves plain-text pages, and a status whose file the theme lacks ends in a
 * bare 500 instead of the page.
 */
public class HelpCenterThemedErrorFilesTest {
    @TempDir
    lateinit var themeDirectory: Path

    /** Writes the operator's theme: HTML pages for 403 and 404, a text page for 503. */
    private fun installTheme() {
        val errors: Path = Files.createDirectories(themeDirectory.resolve("errors"))
        Files.writeString(errors.resolve("404.html"), NOT_FOUND_PAGE)
        Files.writeString(errors.resolve("403.html"), FORBIDDEN_PAGE)
        val maintenance: Path = Files.createDirectories(themeDirectory.resolve("maintenance"))
        Files.writeString(maintenance.resolve("503.txt"), MAINTENANCE_PAGE)
    }

    /** Runs [scenario] against the help center with the theme on its class loader. */
    private fun withHelpCenter(scenario: suspend ApplicationTestBuilder.() -> Unit) {
        installTheme()
        URLClassLoader(arrayOf(themeDirectory.toUri().toURL()), javaClass.classLoader).use { theme ->
            testApplication {
                environment {
                    classLoader = theme
                }
                application {
                    install(StatusPages) {
                        statusFile(
                            HttpStatusCode.NotFound,
                            HttpStatusCode.Forbidden,
                            HttpStatusCode.Gone,
                            filePattern = "errors/#.html",
                        )
                        statusFile(HttpStatusCode.ServiceUnavailable, filePattern = "maintenance/#.txt")
                    }
                    routing {
                        get("/articles/reset-password") {
                            call.respondText("Open Settings, then choose Reset password.")
                        }
                        get("/articles/{slug}") {
                            call.respond(HttpStatusCode.NotFound)
                        }
                        get("/admin/drafts") {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                        get("/articles/legacy-sync") {
                            call.respond(HttpStatusCode.Gone)
                        }
                        get("/search") {
                            call.respond(HttpStatusCode.ServiceUnavailable)
                        }
                    }
                }
                scenario()
            }
        }
    }

    @Test
    fun articleIsServedAsIs() = withHelpCenter {
        val response: HttpResponse = client.get("/articles/reset-password")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Plain))
        assertEquals("Open Settings, then choose Reset password.", response.bodyAsText())
    }

    @Test
    fun missingArticleAndUnknownPathServeTheThemeNotFoundPage() = withHelpCenter {
        val missingArticle: HttpResponse = client.get("/articles/printer-setup")
        val unknownPath: HttpResponse = client.get("/forum")

        assertEquals(HttpStatusCode.NotFound, missingArticle.status)
        assertTrue(missingArticle.contentType()!!.match(ContentType.Text.Html))
        assertEquals(NOT_FOUND_PAGE, missingArticle.bodyAsText())

        assertEquals(HttpStatusCode.NotFound, unknownPath.status)
        assertEquals(NOT_FOUND_PAGE, unknownPath.bodyAsText())
    }

    @Test
    fun eachStatusResolvesItsOwnFile() = withHelpCenter {
        val forbidden: HttpResponse = client.get("/admin/drafts")
        val maintenance: HttpResponse = client.get("/search")

        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertTrue(forbidden.contentType()!!.match(ContentType.Text.Html))
        assertEquals(FORBIDDEN_PAGE, forbidden.bodyAsText())

        assertEquals(HttpStatusCode.ServiceUnavailable, maintenance.status)
        assertTrue(maintenance.contentType()!!.match(ContentType.Text.Plain))
        assertEquals(MAINTENANCE_PAGE, maintenance.bodyAsText())
    }

    @Test
    fun statusWhoseFileTheThemeLacksBecomesAServerError() = withHelpCenter {
        val response: HttpResponse = client.get("/articles/legacy-sync")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("", response.bodyAsText())
    }

    private companion object {
        val NOT_FOUND_PAGE: String = """
            <!DOCTYPE html>
            <html><head><title>Help Center - Not found</title></head>
            <body><h1>We could not find that article</h1></body></html>
        """.trimIndent()

        val FORBIDDEN_PAGE: String = """
            <!DOCTYPE html>
            <html><head><title>Help Center - Forbidden</title></head>
            <body><h1>Editors only</h1></body></html>
        """.trimIndent()

        const val MAINTENANCE_PAGE: String = "Search is down for maintenance. Browse the article index instead."
    }
}
