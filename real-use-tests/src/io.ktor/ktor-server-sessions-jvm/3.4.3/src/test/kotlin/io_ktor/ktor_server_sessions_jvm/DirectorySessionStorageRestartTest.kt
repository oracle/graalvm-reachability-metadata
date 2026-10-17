/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_sessions_jvm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.directorySessionStorage
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Path
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * File-backed server-side session storage created with `directorySessionStorage`.
 *
 * A reading-list service keeps each reader's list in session files under a data directory and
 * gives the browser only an opaque ID in the `READER_ID` cookie. The server is restarted with a
 * fresh application pointed at the same directory, the old cookie still resolves to the saved
 * list, and signing out invalidates the session so its file disappears from disk.
 */
class DirectorySessionStorageRestartTest {

    @Serializable
    data class ReadingList(val reader: String, val books: List<String> = emptyList())

    /** Installs the reading-list routes backed by session files under [dataDir]. */
    private fun Application.readingListModule(dataDir: File) {
        install(Sessions) {
            cookie<ReadingList>("READER_ID", directorySessionStorage(dataDir, cached = false)) {
                cookie.path = "/"
                cookie.httpOnly = true
            }
        }
        routing {
            post("/login") {
                call.sessions.set(ReadingList(reader = call.receiveText()))
                call.respondText("welcome", status = HttpStatusCode.Created)
            }
            post("/books") {
                val list: ReadingList? = call.sessions.get<ReadingList>()
                if (list == null) {
                    call.respondText("sign in first", status = HttpStatusCode.Unauthorized)
                } else {
                    val updated: ReadingList = list.copy(books = list.books + call.receiveText())
                    call.sessions.set(updated)
                    call.respondText(updated.books.size.toString())
                }
            }
            get("/books") {
                val list: ReadingList? = call.sessions.get<ReadingList>()
                call.respondText(list?.let { "${it.reader}: ${it.books.joinToString(" | ")}" } ?: "anonymous")
            }
            post("/logout") {
                call.sessions.clear<ReadingList>()
                call.respondText("bye")
            }
        }
    }

    /** Returns every regular file the storage wrote below [dataDir]. */
    private fun sessionFiles(dataDir: File): List<File> = dataDir.walkTopDown().filter { it.isFile }.toList()

    @Test
    fun sessionFilesOutliveRestartAndAreRemovedOnLogout(@TempDir tempDir: Path) {
        val dataDir: File = tempDir.resolve("sessions").toFile()
        var readerId = ""

        testApplication {
            application { readingListModule(dataDir) }

            val login: HttpResponse = client.post("/login") { setBody("ada") }
            assertEquals(HttpStatusCode.Created, login.status)
            val cookie: Cookie = parseServerSetCookieHeader(login.headers[HttpHeaders.SetCookie]!!)
            assertEquals("READER_ID", cookie.name)
            assertTrue(cookie.httpOnly, "the session cookie is HTTP-only")
            readerId = cookie.value
            assertTrue(readerId.isNotBlank(), "the cookie carries a generated session ID")

            val first: HttpResponse = client.post("/books") {
                header(HttpHeaders.Cookie, "READER_ID=$readerId")
                setBody("Dune")
            }
            assertEquals("1", first.bodyAsText())
            val second: HttpResponse = client.post("/books") {
                header(HttpHeaders.Cookie, "READER_ID=$readerId")
                setBody("Neuromancer")
            }
            assertEquals("2", second.bodyAsText())

            val files: List<File> = sessionFiles(dataDir)
            assertEquals(1, files.size, "one reader means one session file: $files")
            val stored: String = files.single().readText()
            assertTrue(stored.contains("Neuromancer"), "the list is written to disk: $stored")
            assertTrue(!stored.contains(readerId), "the file holds the session data, not the ID")
        }

        testApplication {
            application { readingListModule(dataDir) }

            val restored: String = client.get("/books") { header(HttpHeaders.Cookie, "READER_ID=$readerId") }.bodyAsText()
            assertEquals("ada: Dune | Neuromancer", restored, "a restarted server reads the saved session")
            assertEquals("anonymous", client.get("/books").bodyAsText(), "no cookie means no session")

            val other: HttpResponse = client.post("/login") { setBody("grace") }
            val otherId: String = parseServerSetCookieHeader(other.headers[HttpHeaders.SetCookie]!!).value
            assertTrue(otherId != readerId, "each reader gets a distinct ID")
            assertEquals(2, sessionFiles(dataDir).size, "the second reader gets a second file")

            val logout: HttpResponse = client.post("/logout") { header(HttpHeaders.Cookie, "READER_ID=$readerId") }
            assertEquals("bye", logout.bodyAsText())
            val expired: Cookie = parseServerSetCookieHeader(logout.headers[HttpHeaders.SetCookie]!!)
            assertEquals("READER_ID", expired.name)
            assertEquals("", expired.value, "logout tells the browser to drop the cookie")

            val remaining: List<File> = sessionFiles(dataDir)
            assertEquals(1, remaining.size, "logout deletes only the signed-out reader's file: $remaining")
            assertTrue(remaining.single().readText().contains("grace"))

            val stale: HttpResponse = client.post("/books") {
                header(HttpHeaders.Cookie, "READER_ID=$readerId")
                setBody("Foundation")
            }
            assertEquals(HttpStatusCode.Unauthorized, stale.status, "the old ID no longer resolves")
            val graceView: String = client.get("/books") { header(HttpHeaders.Cookie, "READER_ID=$otherId") }.bodyAsText()
            assertEquals("grace: ", graceView)
        }
    }
}
