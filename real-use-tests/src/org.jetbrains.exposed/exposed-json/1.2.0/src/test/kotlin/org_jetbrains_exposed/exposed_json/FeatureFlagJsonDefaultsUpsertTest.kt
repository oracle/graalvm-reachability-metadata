/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * JSON column defaults, nullability and upserts on PostgreSQL: a feature-flag store declares a
 * `jsonb` rollout column with a typed default and a nullable `json` overrides column, and keeps
 * flags current with `upsert` and `batchUpsert`.
 */
class FeatureFlagJsonDefaultsUpsertTest {

    @Serializable
    data class Rollout(val percentage: Int, val segments: List<String> = emptyList())

    object Flags : Table("feature_flags") {
        private val format: Json = Json { encodeDefaults = true }

        val key = varchar("flag_key", 64)
        val rollout = jsonb<Rollout>("rollout", format).default(Rollout(0))
        val overrides = json<Map<String, Boolean>>("overrides", format).nullable()

        override val primaryKey = PrimaryKey(key)
    }

    companion object {
        private const val PASSWORD = "flags-secret"
        private val containerName: String = "real-use-exposed-json-flags-" + UUID.randomUUID()
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
    fun appliesDefaultsAndUpsertsJsonFlagState() {
        val database = Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = PASSWORD,
        )

        transaction(database) {
            SchemaUtils.create(Flags)
            Flags.insert { it[key] = "dark-mode" }
            exec("INSERT INTO feature_flags (flag_key) VALUES ('legacy-import')")
        }

        val defaults: Map<String, Pair<Rollout, Map<String, Boolean>?>> = transaction(database) {
            Flags.selectAll().associate { it[Flags.key] to (it[Flags.rollout] to it[Flags.overrides]) }
        }
        assertThat(defaults).containsOnlyKeys("dark-mode", "legacy-import")
        assertThat(defaults["dark-mode"]).isEqualTo(Rollout(0) to null)
        assertThat(defaults["legacy-import"]).isEqualTo(Rollout(0) to null)

        transaction(database) {
            Flags.upsert {
                it[key] = "checkout-v2"
                it[rollout] = Rollout(25, listOf("beta"))
                it[overrides] = mapOf("alice" to true)
            }
            Flags.upsert {
                it[key] = "checkout-v2"
                it[rollout] = Rollout(75, listOf("beta", "staff"))
                it[overrides] = mapOf("alice" to true, "bob" to false)
            }
        }

        val checkout: Pair<Rollout, Map<String, Boolean>?> = transaction(database) {
            Flags.selectAll().where { Flags.key eq "checkout-v2" }.single()
                .let { it[Flags.rollout] to it[Flags.overrides] }
        }
        assertThat(checkout.first).isEqualTo(Rollout(75, listOf("beta", "staff")))
        assertThat(checkout.second).containsExactlyInAnyOrderEntriesOf(mapOf("alice" to true, "bob" to false))

        val release: Map<String, Rollout> = linkedMapOf(
            "dark-mode" to Rollout(100),
            "search-v3" to Rollout(50, listOf("eu")),
        )
        transaction(database) {
            Flags.batchUpsert(release.entries, onUpdateExclude = listOf(Flags.overrides)) { (flag, plan) ->
                this[Flags.key] = flag
                this[Flags.rollout] = plan
                this[Flags.overrides] = null
            }
            Flags.update({ Flags.key eq "checkout-v2" }) { it[overrides] = null }
        }

        val percentage = Flags.rollout.extract<String>("percentage").castTo(IntegerColumnType())
        val widelyEnabled: List<Pair<String, Int>> = transaction(database) {
            Flags.select(Flags.key, percentage)
                .where { percentage greaterEq 50 }
                .orderBy(Flags.key)
                .map { it[Flags.key] to it[percentage] }
        }
        assertThat(widelyEnabled).containsExactly("checkout-v2" to 75, "dark-mode" to 100, "search-v3" to 50)

        val withoutOverrides: List<String> = transaction(database) {
            Flags.select(Flags.key)
                .where { Flags.overrides.isNull() }
                .orderBy(Flags.key)
                .map { it[Flags.key] }
        }
        assertThat(withoutOverrides).containsExactly("checkout-v2", "dark-mode", "legacy-import", "search-v3")

        val searchSegments: List<String> = transaction(database) {
            Flags.selectAll().where { Flags.key eq "search-v3" }.single()[Flags.rollout].segments
        }
        assertThat(searchSegments).containsExactly("eu")

        transaction(database) { SchemaUtils.drop(Flags) }
    }
}
