/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_sessions_jvm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.CacheStorage
import io.ktor.server.sessions.SameSite
import io.ktor.server.sessions.SessionProvider
import io.ktor.server.sessions.SessionSerializer
import io.ktor.server.sessions.SessionStorage
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.SessionTrackerById
import io.ktor.server.sessions.SessionTrackerByValue
import io.ktor.server.sessions.SessionTransportHeader
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.directorySessionStorage
import io.ktor.server.sessions.get
import io.ktor.server.sessions.getOrSet
import io.ktor.server.sessions.sessionId
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.sessions.sameSite
import io.ktor.server.testing.testApplication
import io.ktor.util.hex
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

public class KtorServerSessionsJvmTest {
    @Test
    fun cookieSessionRoundTripsReflectionSerializedDataAndClearsCookie(): Unit = testApplication {
        install(Sessions) {
            cookie<ShoppingSession>(ShoppingSessionCookie) {
                serializer = ShoppingSessionSerializer
                cookie.path = "/"
                cookie.httpOnly = true
                cookie.maxAgeInSeconds = 120L
                cookie.sameSite = SameSite.Strict
            }
        }
        routing {
            get("/cart/add") {
                call.sessions.set(ShoppingSession(user = "alice", itemCount = 2, coupon = "SPRING"))
                call.respondText("cart stored")
            }
            get("/cart/view") {
                val session: ShoppingSession? = call.sessions.get()
                call.respondText(session?.let { "${it.user}:${it.itemCount}:${it.coupon}" } ?: "empty")
            }
            get("/cart/get-or-set") {
                val session: ShoppingSession = call.sessions.getOrSet(ShoppingSessionCookie) {
                    ShoppingSession(user = "first", itemCount = 1, coupon = "WELCOME")
                }
                call.respondText("${session.user}:${session.itemCount}:${session.coupon}")
            }
            get("/cart/clear") {
                call.sessions.clear<ShoppingSession>()
                call.respondText("cart cleared")
            }
        }

        val missingResponse = client.get("/cart/view")
        assertThat(missingResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(missingResponse.bodyAsText()).isEqualTo("empty")

        val createResponse = client.get("/cart/add")
        assertThat(createResponse.status).isEqualTo(HttpStatusCode.OK)
        val sessionCookie: String = requireResponseCookie(
            createResponse.headers[HttpHeaders.SetCookie],
            ShoppingSessionCookie
        )
        assertThat(createResponse.headers[HttpHeaders.SetCookie]).contains("HttpOnly")
        assertThat(createResponse.headers[HttpHeaders.SetCookie]).contains("SameSite=Strict")
        assertThat(sessionCookie).contains("$ShoppingSessionCookie=")

        val viewResponse = client.get("/cart/view") {
            header(HttpHeaders.Cookie, sessionCookie)
        }
        assertThat(viewResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(viewResponse.bodyAsText()).isEqualTo("alice:2:SPRING")

        val existingGetOrSetResponse = client.get("/cart/get-or-set") {
            header(HttpHeaders.Cookie, sessionCookie)
        }
        assertThat(existingGetOrSetResponse.bodyAsText()).isEqualTo("alice:2:SPRING")

        val clearResponse = client.get("/cart/clear") {
            header(HttpHeaders.Cookie, sessionCookie)
        }
        assertThat(clearResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(clearResponse.headers[HttpHeaders.SetCookie]).contains("Max-Age=0")
    }

    @Test
    fun cookieSessionIncludesConfiguredSecureDomainAndExtensionAttributes(): Unit = testApplication {
        install(Sessions) {
            cookie<PreferencesSession>(PreferencesSessionCookie) {
                serializer = PreferencesSessionSerializer
                cookie.path = "/preferences"
                cookie.secure = true
                cookie.domain = "example.test"
                cookie.extensions["Priority"] = "High"
            }
        }
        routing {
            get("/preferences/save") {
                call.sessions.set(PreferencesSession(theme = "dark", pageSize = 25, compactMode = true))
                call.respondText("preferences saved")
            }
        }

        val saveResponse = client.get("/preferences/save")
        assertThat(saveResponse.status).isEqualTo(HttpStatusCode.OK)
        val setCookieHeader: String = saveResponse.headers[HttpHeaders.SetCookie]
            ?: error("$PreferencesSessionCookie cookie was not set")
        assertThat(setCookieHeader).startsWith("$PreferencesSessionCookie=")
        assertThat(setCookieHeader).contains("Secure")
        assertThat(setCookieHeader).contains("Domain=example.test")
        assertThat(setCookieHeader).contains("Priority=High")
    }

    @Test
    fun signedCookieSessionRejectsTamperedTransportValues(): Unit = testApplication {
        install(Sessions) {
            cookie<LoginSession>(LoginSessionCookie) {
                serializer = LoginSessionSerializer
                transform(SessionTransportTransformerMessageAuthentication(hex(SigningKeyHex)))
            }
        }
        routing {
            get("/login") {
                call.sessions.set(LoginSession(name = "carol", role = "admin"))
                call.respondText("logged in")
            }
            get("/whoami") {
                val session: LoginSession? = call.sessions.get()
                call.respondText(session?.let { "${it.name}:${it.role}" } ?: "anonymous")
            }
        }

        val loginResponse = client.get("/login")
        val sessionCookie: String = requireResponseCookie(
            loginResponse.headers[HttpHeaders.SetCookie],
            LoginSessionCookie
        )
        assertThat(sessionCookie).contains("$LoginSessionCookie=")

        val acceptedResponse = client.get("/whoami") {
            header(HttpHeaders.Cookie, sessionCookie)
        }
        assertThat(acceptedResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(acceptedResponse.bodyAsText()).isEqualTo("carol:admin")

        val tamperedResponse = client.get("/whoami") {
            header(HttpHeaders.Cookie, tamperCookieValue(sessionCookie))
        }
        assertThat(tamperedResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(tamperedResponse.bodyAsText()).isEqualTo("anonymous")
    }

    @Test
    fun encryptedHeaderSessionUsesOpaqueHeadersForIncomingAndOutgoingData(): Unit = testApplication {
        install(Sessions) {
            register(
                SessionProvider(
                    ApiSessionHeader,
                    ApiHeaderSession::class,
                    SessionTransportHeader(
                        ApiSessionHeader,
                        listOf(
                            SessionTransportTransformerEncrypt(
                                hex(EncryptionKeyHex),
                                hex(SigningKeyHex),
                                ivGenerator = { size: Int ->
                                    ByteArray(size) { index: Int -> (index + 1).toByte() }
                                }
                            )
                        )
                    ),
                    SessionTrackerByValue(ApiHeaderSession::class, ApiHeaderSessionSerializer)
                )
            )
        }
        routing {
            get("/api/issue") {
                call.sessions.set(ApiHeaderSession(subject = "service-client", scope = "read:orders"))
                call.respondText("issued")
            }
            get("/api/current") {
                val session: ApiHeaderSession? = call.sessions.get()
                call.respondText(session?.let { "${it.subject}|${it.scope}" } ?: "missing")
            }
            get("/api/clear") {
                call.sessions.clear<ApiHeaderSession>()
                call.respondText("cleared")
            }
        }

        val issueResponse = client.get("/api/issue")
        val sessionHeader: String = issueResponse.headers[ApiSessionHeader] ?: error("Session header was not set")
        assertThat(sessionHeader).doesNotContain("service-client")
        assertThat(sessionHeader).doesNotContain("read:orders")

        val currentResponse = client.get("/api/current") {
            header(ApiSessionHeader, sessionHeader)
        }
        assertThat(currentResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(currentResponse.bodyAsText()).isEqualTo("service-client|read:orders")

        val clearResponse = client.get("/api/clear") {
            header(ApiSessionHeader, sessionHeader)
        }
        assertThat(clearResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(clearResponse.headers[ApiSessionHeader]).isNull()
    }

    @Test
    fun cookieIdSessionStoresPayloadServerSideAndInvalidatesOnClear(): Unit = testApplication {
        val sequence = AtomicInteger()
        install(Sessions) {
            cookie<ServerSideSession>(ServerSideCookie, SessionStorageMemory()) {
                serializer = ServerSideSessionSerializer
                cookie.path = "/"
                identity { "server-side-${sequence.incrementAndGet()}" }
            }
        }
        routing {
            get("/server/login") {
                call.sessions.set(ServerSideSession(accountId = "account-42", displayName = "Dana"))
                call.respondText("stored")
            }
            get("/server/current") {
                val session: ServerSideSession? = call.sessions.get()
                val id: String = call.sessionId<ServerSideSession>() ?: "no-id"
                call.respondText(session?.let { "$id:${it.accountId}:${it.displayName}" } ?: "none")
            }
            get("/server/logout") {
                call.sessions.clear<ServerSideSession>()
                call.respondText("logged out")
            }
        }

        val loginResponse = client.get("/server/login")
        val idCookie: String = requireResponseCookie(loginResponse.headers[HttpHeaders.SetCookie], ServerSideCookie)
        assertThat(idCookie).contains("server-side-1")
        assertThat(idCookie).doesNotContain("account-42")
        assertThat(idCookie).doesNotContain("Dana")

        val currentResponse = client.get("/server/current") {
            header(HttpHeaders.Cookie, idCookie)
        }
        assertThat(currentResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(currentResponse.bodyAsText()).isEqualTo("server-side-1:account-42:Dana")

        val logoutResponse = client.get("/server/logout") {
            header(HttpHeaders.Cookie, idCookie)
        }
        assertThat(logoutResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(logoutResponse.headers[HttpHeaders.SetCookie]).contains("Max-Age=0")

        val afterLogoutResponse = client.get("/server/current") {
            header(HttpHeaders.Cookie, idCookie)
        }
        assertThat(afterLogoutResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(afterLogoutResponse.bodyAsText()).isEqualTo("none")
    }

    @Test
    fun headerIdSessionStoresOpaqueReferencesAndReusesExistingStorageEntry(): Unit = testApplication {
        val storage = SessionStorageMemory()
        install(Sessions) {
            register(
                SessionProvider(
                    HeaderIdName,
                    HeaderIdSession::class,
                    SessionTransportHeader(HeaderIdName, emptyList()),
                    SessionTrackerById(
                        HeaderIdSession::class,
                        HeaderIdSessionSerializer,
                        storage
                    ) { "header-id-fixed" }
                )
            )
        }
        routing {
            get("/header-id/create") {
                call.sessions.set(HeaderIdSession(token = "token-a", tenant = "tenant-blue"))
                call.respondText("created")
            }
            get("/header-id/read") {
                val session: HeaderIdSession? = call.sessions.get()
                val id: String = call.sessionId<HeaderIdSession>() ?: "no-id"
                call.respondText(session?.let { "$id:${it.token}:${it.tenant}" } ?: "missing")
            }
        }

        val createResponse = client.get("/header-id/create")
        val headerId: String = createResponse.headers[HeaderIdName] ?: error("Header id was not set")
        assertThat(headerId).isEqualTo("header-id-fixed")

        val readResponse = client.get("/header-id/read") {
            header(HeaderIdName, headerId)
        }
        assertThat(readResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(readResponse.bodyAsText()).isEqualTo("header-id-fixed:token-a:tenant-blue")

        val missingResponse = client.get("/header-id/read") {
            header(HeaderIdName, "unknown-id")
        }
        assertThat(missingResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(missingResponse.bodyAsText()).isEqualTo("missing")
    }

    @Test
    fun directoryStoragePersistsOverwritesAndInvalidatesSessionRecords(): Unit = runBlocking {
        val directory = Files.createTempDirectory("ktor-session-storage").toFile()
        try {
            val storage = directorySessionStorage(directory)
            storage.write("record-1", "first-payload")
            assertThat(storage.read("record-1")).isEqualTo("first-payload")

            storage.write("record-1", "updated-payload")
            assertThat(storage.read("record-1")).isEqualTo("updated-payload")

            storage.invalidate("record-1")
            assertThatThrownBy {
                runBlocking { storage.read("record-1") }
            }.isInstanceOf(NoSuchElementException::class.java)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cacheStorageCachesReadsSkipsUnchangedWritesAndInvalidatesEntries(): Unit = runBlocking {
        val delegate = CountingSessionStorage()
        delegate.write("record-1", "initial-payload")
        val storage = CacheStorage(delegate, idleTimeout = 60_000L)

        assertThat(storage.read("record-1")).isEqualTo("initial-payload")
        assertThat(storage.read("record-1")).isEqualTo("initial-payload")
        assertThat(delegate.readCount("record-1")).isEqualTo(1)

        val writesAfterSeed: Int = delegate.writeCount
        storage.write("record-1", "initial-payload")
        assertThat(delegate.writeCount).isEqualTo(writesAfterSeed)

        storage.write("record-1", "updated-payload")
        assertThat(delegate.value("record-1")).isEqualTo("updated-payload")
        assertThat(storage.read("record-1")).isEqualTo("updated-payload")
        assertThat(delegate.readCount("record-1")).isEqualTo(2)

        storage.invalidate("record-1")
        assertThat(delegate.value("record-1")).isNull()
        assertThatThrownBy {
            runBlocking { storage.read("record-1") }
        }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun transportTransformersRoundTripAndAuthenticateStandaloneValues(): Unit {
        val signer = SessionTransportTransformerMessageAuthentication(hex(SigningKeyHex))
        val signed: String = signer.transformWrite("session-payload")
        assertThat(signed).isNotEqualTo("session-payload")
        assertThat(signer.transformRead(signed)).isEqualTo("session-payload")
        assertThat(signer.transformRead(tamperValue(signed))).isNull()

        val encryptor = SessionTransportTransformerEncrypt(
            hex(EncryptionKeyHex),
            hex(SigningKeyHex),
            ivGenerator = { size: Int -> ByteArray(size) { index: Int -> (size - index).toByte() } }
        )
        val encrypted: String = encryptor.transformWrite("confidential-session-payload")
        assertThat(encrypted).doesNotContain("confidential")
        assertThat(encryptor.transformRead(encrypted)).isEqualTo("confidential-session-payload")
        assertThat(encryptor.transformRead(tamperValue(encrypted))).isNull()
    }

    private fun requireResponseCookie(setCookieHeader: String?, cookieName: String): String {
        val header: String = setCookieHeader ?: error("$cookieName cookie was not set")
        assertThat(header).startsWith("$cookieName=")
        return header.substringBefore(';')
    }

    private fun tamperCookieValue(cookie: String): String {
        val name: String = cookie.substringBefore('=')
        val value: String = cookie.substringAfter('=')
        return "$name=${tamperValue(value)}"
    }

    private fun tamperValue(value: String): String = if (value.last() == 'a') {
        value.dropLast(1) + 'b'
    } else {
        value.dropLast(1) + 'a'
    }

    private class CountingSessionStorage : SessionStorage {
        private val records: MutableMap<String, String> = mutableMapOf()
        private val readCounts: MutableMap<String, Int> = mutableMapOf()

        var writeCount: Int = 0
            private set

        override suspend fun read(id: String): String {
            readCounts[id] = readCount(id) + 1
            return records[id] ?: throw NoSuchElementException("No session record for $id")
        }

        override suspend fun write(id: String, value: String) {
            writeCount++
            records[id] = value
        }

        override suspend fun invalidate(id: String) {
            records.remove(id)
        }

        fun readCount(id: String): Int = readCounts[id] ?: 0

        fun value(id: String): String? = records[id]
    }

    private object ShoppingSessionSerializer : SessionSerializer<ShoppingSession> {
        override fun serialize(session: ShoppingSession): String = listOf(
            session.user,
            session.itemCount.toString(),
            session.coupon
        ).joinToString("|")

        override fun deserialize(text: String): ShoppingSession {
            val parts: List<String> = text.split('|')
            return ShoppingSession(parts[0], parts[1].toInt(), parts[2])
        }
    }

    private object PreferencesSessionSerializer : SessionSerializer<PreferencesSession> {
        override fun serialize(session: PreferencesSession): String = listOf(
            session.theme,
            session.pageSize.toString(),
            session.compactMode.toString()
        ).joinToString("|")

        override fun deserialize(text: String): PreferencesSession {
            val parts: List<String> = text.split('|')
            return PreferencesSession(parts[0], parts[1].toInt(), parts[2].toBoolean())
        }
    }

    private object LoginSessionSerializer : SessionSerializer<LoginSession> {
        override fun serialize(session: LoginSession): String = "${session.name}|${session.role}"

        override fun deserialize(text: String): LoginSession {
            val parts: List<String> = text.split('|')
            return LoginSession(parts[0], parts[1])
        }
    }

    private object ApiHeaderSessionSerializer : SessionSerializer<ApiHeaderSession> {
        override fun serialize(session: ApiHeaderSession): String = "${session.subject}|${session.scope}"

        override fun deserialize(text: String): ApiHeaderSession {
            val parts: List<String> = text.split('|')
            return ApiHeaderSession(parts[0], parts[1])
        }
    }

    private object ServerSideSessionSerializer : SessionSerializer<ServerSideSession> {
        override fun serialize(session: ServerSideSession): String = "${session.accountId}|${session.displayName}"

        override fun deserialize(text: String): ServerSideSession {
            val parts: List<String> = text.split('|')
            return ServerSideSession(parts[0], parts[1])
        }
    }

    private object HeaderIdSessionSerializer : SessionSerializer<HeaderIdSession> {
        override fun serialize(session: HeaderIdSession): String = "${session.token}|${session.tenant}"

        override fun deserialize(text: String): HeaderIdSession {
            val parts: List<String> = text.split('|')
            return HeaderIdSession(parts[0], parts[1])
        }
    }

    private companion object {
        private const val ShoppingSessionCookie: String = "SHOPPING_SESSION"
        private const val PreferencesSessionCookie: String = "PREFERENCES_SESSION"
        private const val LoginSessionCookie: String = "LOGIN_SESSION"
        private const val ServerSideCookie: String = "SERVER_SIDE_SESSION"
        private const val ApiSessionHeader: String = "X-Api-Session"
        private const val HeaderIdName: String = "X-Header-Session-Id"
        private const val EncryptionKeyHex: String = "00112233445566778899aabbccddeeff"
        private const val SigningKeyHex: String =
            "0123456789abcdeffedcba98765432100123456789abcdeffedcba9876543210"
    }
}

public data class ShoppingSession(val user: String, val itemCount: Int, val coupon: String)

public data class PreferencesSession(val theme: String, val pageSize: Int, val compactMode: Boolean)

public data class LoginSession(val name: String, val role: String)

public data class ApiHeaderSession(val subject: String, val scope: String)

public data class ServerSideSession(val accountId: String, val displayName: String)

public data class HeaderIdSession(val token: String, val tenant: String)
