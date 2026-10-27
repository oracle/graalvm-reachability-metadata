/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_json

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.json.castToJson
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Casting text to JSON with `castToJson`: a webhook inbox stores raw payloads as plain text,
 * then decodes them into typed events and filters them by JSON field on PostgreSQL.
 */
class WebhookInboxCastToJsonTest {

    @Serializable
    data class Actor(val login: String, val admin: Boolean = false)

    @Serializable
    data class WebhookEvent(
        val type: String,
        val repository: String,
        val actor: Actor,
        val labels: List<String> = emptyList(),
    )

    object Inbox : Table("webhook_inbox") {
        val id = integer("id").autoIncrement()
        val deliveryId = varchar("delivery_id", 36).uniqueIndex()
        val payload = text("payload")

        override val primaryKey = PrimaryKey(id)
    }

    companion object {
        private const val PASSWORD = "inbox-secret"
        private val containerName: String = "real-use-exposed-json-inbox-" + UUID.randomUUID()
        private var port: Int = 0

        private val jdbcUrl: String
            get() = "jdbc:postgresql://127.0.0.1:$port/postgres"

        @BeforeAll
        @JvmStatic
        fun startPostgres() {
            port = ServerSocket(0).use { it.localPort }
            docker(
                "run", "--rm", "-d", "--name", containerName,
                "-e", "POSTGRES_PASSWORD=$PASSWORD",
                "-p", "127.0.0.1:$port:5432",
                "postgres:18-alpine",
            )
            awaitConnection(TimeUnit.SECONDS.toMillis(120))
        }

        @AfterAll
        @JvmStatic
        fun stopPostgres() {
            docker("rm", "-f", containerName)
        }

        private fun docker(vararg args: String) {
            val process = ProcessBuilder(listOf("docker") + args).inheritIO().start()
            check(process.waitFor(5, TimeUnit.MINUTES)) { "docker ${args.first()} timed out" }
            check(process.exitValue() == 0) { "docker ${args.first()} failed with ${process.exitValue()}" }
        }

        /** Polls with a bounded deadline until the server accepts TCP connections. */
        private fun awaitConnection(timeoutMillis: Long) {
            val deadline = System.currentTimeMillis() + timeoutMillis
            var lastFailure: Exception? = null
            while (System.currentTimeMillis() < deadline) {
                try {
                    DriverManager.getConnection(jdbcUrl, "postgres", PASSWORD).use { connection ->
                        connection.createStatement().use { it.execute("SELECT 1") }
                    }
                    return
                } catch (e: Exception) {
                    lastFailure = e
                    Thread.sleep(250)
                }
            }
            throw IllegalStateException("PostgreSQL did not become reachable", lastFailure)
        }
    }

    @Test
    fun decodesAndFiltersRawTextPayloadsAsJson() {
        val config = HikariConfig().apply {
            jdbcUrl = WebhookInboxCastToJsonTest.jdbcUrl
            username = "postgres"
            password = PASSWORD
            maximumPoolSize = 2
            poolName = "webhook-inbox"
        }
        HikariDataSource(config).use { dataSource ->
            val database = Database.connect(dataSource)
            val deliveries: Map<String, String> = linkedMapOf(
                "d-1" to """{"type":"push","repository":"graal","actor":{"login":"ada"}}""",
                "d-2" to """{"type":"issues","repository":"graal","actor":{"login":"grace","admin":true},"labels":["bug","native"]}""",
                "d-3" to """{"type":"push","repository":"exposed","actor":{"login":"linus"},"labels":["ci"]}""",
            )

            transaction(database) {
                SchemaUtils.create(Inbox)
                Inbox.batchInsert(deliveries.entries) { (delivery, body) ->
                    this[Inbox.deliveryId] = delivery
                    this[Inbox.payload] = body
                }
            }

            val event = Inbox.payload.castToJson<WebhookEvent>()
            val decoded: Map<String, WebhookEvent> = transaction(database) {
                Inbox.select(Inbox.deliveryId, event)
                    .orderBy(Inbox.deliveryId)
                    .associate { it[Inbox.deliveryId] to it[event] }
            }
            assertThat(decoded).containsOnlyKeys("d-1", "d-2", "d-3")
            assertThat(decoded["d-1"]).isEqualTo(WebhookEvent("push", "graal", Actor("ada")))
            assertThat(decoded["d-2"])
                .isEqualTo(WebhookEvent("issues", "graal", Actor("grace", admin = true), listOf("bug", "native")))
            assertThat(decoded.getValue("d-3").labels).containsExactly("ci")

            val eventType = event.extract<String>("type")
            val actorLogin = event.extract<String>("actor", "login")
            val pushes: List<Pair<String, String>> = transaction(database) {
                Inbox.select(Inbox.deliveryId, actorLogin)
                    .where { eventType eq "push" }
                    .orderBy(actorLogin to SortOrder.DESC)
                    .map { it[Inbox.deliveryId] to it[actorLogin] }
            }
            assertThat(pushes).containsExactly("d-3" to "linus", "d-1" to "ada")

            val labels = event.extract<List<String>>("labels", toScalar = false)
            val graalLabels: List<List<String>> = transaction(database) {
                Inbox.select(labels)
                    .where { event.extract<String>("repository") eq "graal" }
                    .orderBy(Inbox.deliveryId)
                    .map { it.getOrNull(labels) ?: emptyList() }
            }
            assertThat(graalLabels).containsExactly(emptyList(), listOf("bug", "native"))

            transaction(database) { SchemaUtils.drop(Inbox) }
        }
    }
}
