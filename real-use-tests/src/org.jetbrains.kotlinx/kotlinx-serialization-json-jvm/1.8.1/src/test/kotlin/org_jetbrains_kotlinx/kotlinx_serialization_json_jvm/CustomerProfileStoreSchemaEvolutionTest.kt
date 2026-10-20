/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises schema evolution of JSON documents persisted in a SQL database: rows written by an
 * older document shape decode into the current one through property defaults, `@JsonNames`
 * renames, and `ignoreUnknownKeys`, while incompatible rows surface as `MissingFieldException`
 * or `SerializationException`.
 *
 * A customer service keeps each profile as a JSON document in an H2 `JSON` column. On startup it
 * loads rows written by the previous release, quarantines rows it cannot read, and migrates the
 * readable ones by writing them back in the current shape.
 */
@OptIn(ExperimentalSerializationApi::class)
public class CustomerProfileStoreSchemaEvolutionTest {

    @Serializable
    public enum class Tier {
        @SerialName("standard") STANDARD,
        @SerialName("gold") GOLD,
    }

    @Serializable
    public data class Preferences(
        val newsletter: Boolean = false,
        val language: String = "en",
    )

    @Serializable
    public data class Address(val city: String, val postcode: String)

    /** Current document shape; `name` was renamed and `phone` dropped since the previous release. */
    @Serializable
    public data class CustomerProfile(
        val id: String,
        @JsonNames("name") val displayName: String,
        val tier: Tier = Tier.STANDARD,
        val preferences: Preferences = Preferences(),
        val addresses: List<Address> = emptyList(),
    )

    /** Outcome of loading every stored row: readable profiles and the reasons others were rejected. */
    public data class LoadResult(val profiles: List<CustomerProfile>, val rejected: Map<Long, String>)

    /** Stores profiles in one table, keyed by a surrogate row id, with the document in a `JSON` column. */
    public class ProfileStore(private val connection: Connection, private val json: Json) {

        public fun createSchema() {
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE profiles (row_id BIGINT PRIMARY KEY, doc JSON NOT NULL)")
            }
        }

        public fun insertRaw(rows: Map<Long, String>) {
            connection.prepareStatement("INSERT INTO profiles (row_id, doc) VALUES (?, ? FORMAT JSON)").use { insert ->
                for ((rowId: Long, doc: String) in rows) {
                    insert.setLong(1, rowId)
                    insert.setString(2, doc)
                    insert.addBatch()
                }
                insert.executeBatch()
            }
        }

        public fun loadAll(): LoadResult {
            val profiles: MutableList<CustomerProfile> = mutableListOf()
            val rejected: MutableMap<Long, String> = linkedMapOf()
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT row_id, doc FROM profiles ORDER BY row_id").use { rows: ResultSet ->
                    while (rows.next()) {
                        val rowId: Long = rows.getLong("row_id")
                        val doc: String = rows.getString("doc")
                        try {
                            profiles += json.decodeFromString<CustomerProfile>(doc)
                        } catch (e: MissingFieldException) {
                            rejected[rowId] = "missing " + e.missingFields.sorted().joinToString(",")
                        } catch (e: SerializationException) {
                            rejected[rowId] = "malformed"
                        }
                    }
                }
            }
            return LoadResult(profiles, rejected)
        }

        public fun rewrite(rowIdsByProfileId: Map<String, Long>, profiles: List<CustomerProfile>) {
            val update: PreparedStatement = connection.prepareStatement(
                "UPDATE profiles SET doc = ? FORMAT JSON WHERE row_id = ?",
            )
            update.use {
                for (profile: CustomerProfile in profiles) {
                    update.setString(1, json.encodeToString(profile))
                    update.setLong(2, rowIdsByProfileId.getValue(profile.id))
                    update.addBatch()
                }
                update.executeBatch()
            }
        }

        public fun rawDocument(rowId: Long): String =
            connection.prepareStatement("SELECT doc FROM profiles WHERE row_id = ?").use { select ->
                select.setLong(1, rowId)
                select.executeQuery().use { rows: ResultSet ->
                    assertTrue(rows.next())
                    rows.getString(1)
                }
            }
    }

    private val json: Json = Json { ignoreUnknownKeys = true }

    private lateinit var connection: Connection

    private lateinit var store: ProfileStore

    @BeforeEach
    public fun openDatabase() {
        connection = DriverManager.getConnection("jdbc:h2:mem:customer-profiles", "sa", "")
        store = ProfileStore(connection, json)
        store.createSchema()
    }

    @AfterEach
    public fun closeDatabase() {
        connection.close()
    }

    @Test
    public fun previousReleaseRowsDecodeIntoCurrentShapeAndBrokenRowsAreQuarantined() {
        store.insertRaw(
            linkedMapOf(
                1L to """{"id":"c-100","name":"Ada Lovelace","phone":"+44 20 7946 0001"}""",
                2L to """{"id":"c-200","name":"Grace Hopper","tier":"gold","preferences":{"newsletter":true}}""",
                3L to """{"phone":"+1 555 0100"}""",
                4L to """{"id":"c-400","displayName":"Alan Turing","tier":"platinum"}""",
                5L to """{"id":"c-500","displayName":"Edsger Dijkstra","addresses":[{"city":"Austin","postcode":"78701"}]}""",
            ),
        )

        val loaded: LoadResult = store.loadAll()

        assertEquals(
            listOf(
                CustomerProfile(id = "c-100", displayName = "Ada Lovelace"),
                CustomerProfile(
                    id = "c-200",
                    displayName = "Grace Hopper",
                    tier = Tier.GOLD,
                    preferences = Preferences(newsletter = true, language = "en"),
                ),
                CustomerProfile(
                    id = "c-500",
                    displayName = "Edsger Dijkstra",
                    addresses = listOf(Address(city = "Austin", postcode = "78701")),
                ),
            ),
            loaded.profiles,
        )
        assertEquals(mapOf(3L to "missing displayName,id", 4L to "malformed"), loaded.rejected)
    }

    @Test
    public fun migrationRewritesRowsInCurrentShapeAndReloadsUnchanged() {
        store.insertRaw(
            linkedMapOf(
                10L to """{"id":"c-1","name":"Barbara Liskov","phone":"+1 555 0199","loyaltyPoints":1200}""",
                11L to """{"id":"c-2","name":"Donald Knuth","tier":"gold","preferences":{"language":"de"}}""",
            ),
        )
        val before: LoadResult = store.loadAll()
        assertTrue(before.rejected.isEmpty())

        val upgraded: List<CustomerProfile> = before.profiles.map { profile: CustomerProfile ->
            if (profile.id == "c-1") profile.copy(tier = Tier.GOLD) else profile
        }
        store.rewrite(mapOf("c-1" to 10L, "c-2" to 11L), upgraded)

        val migratedDoc: String = store.rawDocument(10L)
        assertTrue(migratedDoc.contains("\"displayName\":\"Barbara Liskov\""), migratedDoc)
        assertTrue(migratedDoc.contains("\"tier\":\"gold\""), migratedDoc)
        assertFalse(migratedDoc.contains("phone"), migratedDoc)
        assertFalse(migratedDoc.contains("loyaltyPoints"), migratedDoc)
        assertFalse(migratedDoc.contains("\"name\""), migratedDoc)

        val strict: Json = Json { ignoreUnknownKeys = false }
        assertEquals(upgraded[0], strict.decodeFromString<CustomerProfile>(migratedDoc))
        assertEquals(
            CustomerProfile(
                id = "c-2",
                displayName = "Donald Knuth",
                tier = Tier.GOLD,
                preferences = Preferences(newsletter = false, language = "de"),
            ),
            strict.decodeFromString<CustomerProfile>(store.rawDocument(11L)),
        )

        val after: LoadResult = store.loadAll()
        assertEquals(upgraded, after.profiles)
        assertTrue(after.rejected.isEmpty())
    }
}
