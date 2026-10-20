/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises schema evolution of stored `Cbor` documents across application versions:
 * unknown-key handling, defaults for fields a writer did not know, and missing required fields.
 *
 * A profile service keeps each user profile as a CBOR blob in an H2 table. Version 1 of the
 * service wrote a small profile; version 2 added a locale, enum-keyed feature flags and
 * integer-keyed login counters. Both versions keep running during a rolling upgrade, so each
 * must read what the other wrote.
 */
@OptIn(ExperimentalSerializationApi::class)
public class ProfileStoreSchemaEvolutionCborTest {

    @Serializable
    @JvmInline
    public value class UserId(val value: Long)

    @Serializable
    public enum class Feature { DARK_MODE, BETA_SEARCH, NEWSLETTER }

    /** The profile as version 1 of the service wrote it. */
    @Serializable
    public data class ProfileV1(val id: UserId, val displayName: String, val email: String)

    /** The profile as version 2 of the service writes it. */
    @Serializable
    public data class ProfileV2(
        val id: UserId,
        val displayName: String,
        val email: String,
        val locale: String = "en",
        val features: Map<Feature, Boolean> = emptyMap(),
        val loginsByWeekday: Map<Int, Int> = emptyMap(),
    )

    /** An export from a legacy importer that never recorded an email address. */
    @Serializable
    public data class LegacyContact(val id: UserId, val displayName: String)

    /** Persists profile documents as CBOR blobs keyed by user id. */
    public class ProfileStore(private val connection: Connection) {

        init {
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE profile (id BIGINT PRIMARY KEY, document VARBINARY NOT NULL)")
            }
        }

        public fun save(id: UserId, document: ByteArray): Unit {
            connection.prepareStatement("MERGE INTO profile (id, document) KEY (id) VALUES (?, ?)")
                .use { statement: PreparedStatement ->
                    statement.setLong(1, id.value)
                    statement.setBytes(2, document)
                    statement.executeUpdate()
                }
        }

        public fun load(id: UserId): ByteArray =
            connection.prepareStatement("SELECT document FROM profile WHERE id = ?").use { statement: PreparedStatement ->
                statement.setLong(1, id.value)
                statement.executeQuery().use { rows: ResultSet ->
                    check(rows.next()) { "no profile ${id.value}" }
                    rows.getBytes(1)
                }
            }
    }

    /** The strict codec the service used before the rolling upgrade. */
    private val strictCbor: Cbor = Cbor { useDefiniteLengthEncoding = true }

    /** The codec both versions use once they must tolerate each other's documents. */
    private val tolerantCbor: Cbor = Cbor {
        useDefiniteLengthEncoding = true
        ignoreUnknownKeys = true
    }

    private fun <T> withStore(database: String, block: (ProfileStore) -> T): T =
        DriverManager.getConnection("jdbc:h2:mem:$database").use { connection: Connection ->
            block(ProfileStore(connection))
        }

    @Test
    fun versionTwoReadsProfilesWrittenByVersionOne(): Unit = withStore("profiles_v1_to_v2") { store: ProfileStore ->
        val ada: ProfileV1 = ProfileV1(UserId(1L), "Ada", "ada@example.org")
        store.save(ada.id, strictCbor.encodeToByteArray(ada))

        val upgraded: ProfileV2 = strictCbor.decodeFromByteArray<ProfileV2>(store.load(ada.id))

        assertEquals(ProfileV2(UserId(1L), "Ada", "ada@example.org"), upgraded)
        assertEquals("en", upgraded.locale)
        assertEquals(emptyMap<Feature, Boolean>(), upgraded.features)

        val edited: ProfileV2 = upgraded.copy(
            locale = "fr",
            features = mapOf(Feature.DARK_MODE to true, Feature.NEWSLETTER to false),
            loginsByWeekday = mapOf(1 to 3, 5 to 1),
        )
        store.save(edited.id, strictCbor.encodeToByteArray(edited))

        val reloaded: ProfileV2 = strictCbor.decodeFromByteArray<ProfileV2>(store.load(edited.id))
        assertEquals(edited, reloaded)
        assertEquals(true, reloaded.features[Feature.DARK_MODE])
        assertEquals(4, reloaded.loginsByWeekday.values.sum())
    }

    @Test
    fun versionOneReadsProfilesWrittenByVersionTwoOnlyWhenTolerant(): Unit =
        withStore("profiles_v2_to_v1") { store: ProfileStore ->
            val grace: ProfileV2 = ProfileV2(
                id = UserId(2L),
                displayName = "Grace",
                email = "grace@example.org",
                locale = "en-US",
                features = mapOf(Feature.BETA_SEARCH to true),
                loginsByWeekday = mapOf(2 to 7),
            )
            store.save(grace.id, tolerantCbor.encodeToByteArray(grace))
            val stored: ByteArray = store.load(grace.id)

            val rejected: SerializationException = assertThrows(SerializationException::class.java) {
                strictCbor.decodeFromByteArray<ProfileV1>(stored)
            }
            assertTrue(rejected.message.orEmpty().contains("locale")) { rejected.message.orEmpty() }

            val readByV1: ProfileV1 = tolerantCbor.decodeFromByteArray<ProfileV1>(stored)
            assertEquals(ProfileV1(UserId(2L), "Grace", "grace@example.org"), readByV1)
        }

    @Test
    fun storesEnumAndIntegerMapKeysAsNativeCborKeys(): Unit {
        val profile: ProfileV2 = ProfileV2(
            id = UserId(3L),
            displayName = "Lin",
            email = "l@x",
            features = mapOf(Feature.NEWSLETTER to true),
            loginsByWeekday = mapOf(6 to 2),
        )
        val features: ByteArray = byteArrayOf(
            0x68, *"features".encodeToByteArray(),
            0xA1.toByte(), 0x6A, *"NEWSLETTER".encodeToByteArray(), 0xF5.toByte(),
        )
        val logins: ByteArray = byteArrayOf(
            0x6F, *"loginsByWeekday".encodeToByteArray(),
            0xA1.toByte(), 0x06, 0x02,
        )
        val expected: ByteArray = byteArrayOf(
            0xA5.toByte(),
            0x62, *"id".encodeToByteArray(), 0x03,
            0x6B, *"displayName".encodeToByteArray(), 0x63, *"Lin".encodeToByteArray(),
            0x65, *"email".encodeToByteArray(), 0x63, *"l@x".encodeToByteArray(),
        ) + features + logins

        assertArrayEquals(expected, strictCbor.encodeToByteArray(profile))
        assertEquals(profile, strictCbor.decodeFromByteArray<ProfileV2>(expected))
    }

    @Test
    fun rejectsImportedContactsWithoutAnEmailAddress(): Unit = withStore("profiles_import") { store: ProfileStore ->
        val contact: LegacyContact = LegacyContact(UserId(4L), "Edsger")
        store.save(contact.id, tolerantCbor.encodeToByteArray(contact))

        val missing: MissingFieldException = assertThrows(MissingFieldException::class.java) {
            tolerantCbor.decodeFromByteArray<ProfileV2>(store.load(contact.id))
        }

        assertEquals(listOf("email"), missing.missingFields)
    }
}
