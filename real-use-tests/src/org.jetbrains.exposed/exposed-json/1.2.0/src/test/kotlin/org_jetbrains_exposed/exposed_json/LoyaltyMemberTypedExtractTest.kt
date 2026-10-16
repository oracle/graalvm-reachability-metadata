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
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.json
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
 * Typed scalar and sub-document extraction from a `json` column on PostgreSQL: a loyalty program
 * reads points, flags and spend as Kotlin numbers and booleans, nested tier objects as data
 * classes, and array elements by index straight out of stored member profiles.
 */
class LoyaltyMemberTypedExtractTest {

    @Serializable
    data class Tier(val name: String, val multiplier: Double, val perks: List<String>)

    @Serializable
    data class Address(val city: String, val country: String)

    @Serializable
    data class MemberProfile(
        val displayName: String,
        val points: Int,
        val verified: Boolean,
        val lifetimeSpend: Double,
        val tier: Tier,
        val addresses: List<Address>,
    )

    object Members : Table("loyalty_members") {
        val id = integer("id").autoIncrement()
        val memberNo = varchar("member_no", 16).uniqueIndex()
        val profile = json<MemberProfile>("profile", Json)

        override val primaryKey = PrimaryKey(id)
    }

    companion object {
        private const val PASSWORD = "loyalty-secret"
        private val containerName: String = "real-use-exposed-json-" + UUID.randomUUID()
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
    fun readsTypedScalarsSubDocumentsAndArrayElements() {
        val database = Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = PASSWORD,
        )
        val gold = Tier("gold", 1.5, listOf("lounge", "free-shipping"))
        val silver = Tier("silver", 1.25, listOf("free-shipping"))
        val basic = Tier("basic", 1.0, emptyList())
        val members: Map<String, MemberProfile> = linkedMapOf(
            "M-001" to MemberProfile(
                "Ada", 4200, true, 1830.5, gold,
                listOf(Address("London", "GB"), Address("Paris", "FR")),
            ),
            "M-002" to MemberProfile("Grace", 950, false, 240.75, basic, listOf(Address("Arlington", "US"))),
            "M-003" to MemberProfile(
                "Linus", 2600, true, 995.0, silver,
                listOf(Address("Helsinki", "FI"), Address("Portland", "US")),
            ),
        )

        transaction(database) {
            SchemaUtils.create(Members)
            Members.batchInsert(members.entries) { (memberNo, profile) ->
                this[Members.memberNo] = memberNo
                this[Members.profile] = profile
            }
        }

        val points = Members.profile.extract<Int>("points")
        val verified = Members.profile.extract<Boolean>("verified")
        val spend = Members.profile.extract<Double>("lifetimeSpend")
        val balances: Map<String, Triple<Int, Boolean, Double>> = transaction(database) {
            Members.select(Members.memberNo, points, verified, spend)
                .associate { it[Members.memberNo] to Triple(it[points], it[verified], it[spend]) }
        }
        assertThat(balances).containsOnlyKeys("M-001", "M-002", "M-003")
        assertThat(balances["M-001"]).isEqualTo(Triple(4200, true, 1830.5))
        assertThat(balances["M-002"]).isEqualTo(Triple(950, false, 240.75))
        assertThat(balances["M-003"]).isEqualTo(Triple(2600, true, 995.0))

        val tier = Members.profile.extract<Tier>("tier", toScalar = false)
        val tiers: List<Pair<String, Tier>> = transaction(database) {
            Members.select(Members.memberNo, tier)
                .orderBy(Members.memberNo)
                .map { it[Members.memberNo] to it[tier] }
        }
        assertThat(tiers).containsExactly("M-001" to gold, "M-002" to basic, "M-003" to silver)

        val primaryCity = Members.profile.extract<String>("addresses", "0", "city")
        val secondAddress = Members.profile.extract<Address>("addresses", "1", toScalar = false)
        val shipping: List<Triple<String, String, Address?>> = transaction(database) {
            Members.select(Members.memberNo, primaryCity, secondAddress)
                .orderBy(Members.memberNo)
                .map { Triple(it[Members.memberNo], it[primaryCity], it.getOrNull(secondAddress)) }
        }
        assertThat(shipping).containsExactly(
            Triple("M-001", "London", Address("Paris", "FR")),
            Triple("M-002", "Arlington", null),
            Triple("M-003", "Helsinki", Address("Portland", "US")),
        )

        val pointsForFilter = Members.profile.extract<String>("points").castTo(IntegerColumnType())
        val tierName = Members.profile.extract<String>("tier", "name")
        val rewardable: List<Pair<String, String>> = transaction(database) {
            Members.select(Members.memberNo, tierName)
                .where { (pointsForFilter greaterEq 2000) and (Members.profile.extract<String>("verified") eq "true") }
                .orderBy(pointsForFilter to SortOrder.DESC)
                .map { it[Members.memberNo] to it[tierName] }
        }
        assertThat(rewardable).containsExactly("M-001" to "gold", "M-003" to "silver")

        transaction(database) { SchemaUtils.drop(Members) }
    }
}
