/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Schema evolution between message revisions decoded by `ProtoBuf`: unknown fields skipped, missing fields defaulted.
 *
 * A customer-profile service is rolled out gradually, so revision 1 and revision 2 of the profile
 * message are on the wire at the same time. Revision 2 renamed a field, added fields of every wire
 * type and a nested message, and retired a field number. Each revision must read what the other
 * wrote, relying only on field numbers, the defaults it declares and the `encodeDefaults` switch.
 */
@OptIn(ExperimentalSerializationApi::class)
class CustomerProfileSchemaEvolutionTest {

    /** Revision 1 of the profile, still deployed on older nodes. */
    @Serializable
    data class ProfileV1(
        @ProtoNumber(1) val customerId: Long,
        @ProtoNumber(2) val displayName: String,
        @ProtoNumber(3) val loyaltyPoints: Int = 0,
        @ProtoNumber(4) val legacyFaxNumber: String = "",
    )

    @Serializable
    data class Address(
        @ProtoNumber(1) val city: String,
        @ProtoNumber(2) val postalCode: String,
    )

    /**
     * Revision 2: `displayName` is now `fullName` under the same number, field 4 is retired, and
     * fields 5 to 9 are new. Every new field has a default so revision 1 payloads stay readable.
     */
    @Serializable
    data class ProfileV2(
        @ProtoNumber(1) val customerId: Long,
        @ProtoNumber(2) val fullName: String,
        @ProtoNumber(3) val loyaltyPoints: Int = 0,
        @ProtoNumber(5) val tags: List<String> = emptyList(),
        @ProtoNumber(6) @ProtoType(ProtoIntegerType.FIXED) val regionCode: Int = 0,
        @ProtoNumber(7) val creditLimit: Double = 0.0,
        @ProtoNumber(8) val shippingAddress: Address? = null,
        @ProtoNumber(9) val marketingOptIn: Boolean = true,
    )

    /** Revision 3 drafts a required field, which revision 1 payloads can never satisfy. */
    @Serializable
    data class ProfileV3(
        @ProtoNumber(1) val customerId: Long,
        @ProtoNumber(2) val fullName: String,
        @ProtoNumber(10) val accountManager: String,
    )

    private val protoBuf: ProtoBuf = ProtoBuf

    private val protoBufWithDefaults: ProtoBuf = ProtoBuf { encodeDefaults = true }

    @Test
    fun newerReaderAppliesDefaultsToFieldsAnOlderWriterNeverSent() {
        val written = ProfileV1(customerId = 7_001L, displayName = "Grace Hopper", loyaltyPoints = 320)

        val restored: ProfileV2 = protoBuf.decodeFromByteArray(protoBuf.encodeToByteArray(written))

        assertEquals(7_001L, restored.customerId)
        assertEquals("Grace Hopper", restored.fullName)
        assertEquals(320, restored.loyaltyPoints)
        assertTrue(restored.tags.isEmpty())
        assertEquals(0, restored.regionCode)
        assertEquals(0.0, restored.creditLimit)
        assertEquals(null, restored.shippingAddress)
        assertTrue(restored.marketingOptIn)
    }

    @Test
    fun newerReaderSkipsTheRetiredFieldAnOlderWriterStillSends() {
        val written = ProfileV1(customerId = 12L, displayName = "Alan Turing", legacyFaxNumber = "+44 20 7946 0000")

        val restored: ProfileV2 = protoBuf.decodeFromByteArray(protoBuf.encodeToByteArray(written))

        assertEquals(ProfileV2(customerId = 12L, fullName = "Alan Turing"), restored)
    }

    @Test
    fun olderReaderSkipsUnknownFieldsOfEveryWireType() {
        val written = ProfileV2(
            customerId = 9_000_000_123L,
            fullName = "Katherine Johnson",
            loyaltyPoints = 1_250,
            tags = listOf("vip", "beta-tester"),
            regionCode = 49,
            creditLimit = 2_500.75,
            shippingAddress = Address(city = "Hampton", postalCode = "23666"),
            marketingOptIn = false,
        )

        val bytes: ByteArray = protoBuf.encodeToByteArray(written)
        val restored: ProfileV1 = protoBuf.decodeFromByteArray(bytes)

        assertEquals(
            ProfileV1(customerId = 9_000_000_123L, displayName = "Katherine Johnson", loyaltyPoints = 1_250),
            restored,
        )
        // The old node writes back only what it knows, so the new fields are lost on its re-encode.
        val reencoded: ProfileV2 = protoBuf.decodeFromByteArray(protoBuf.encodeToByteArray(restored))
        assertEquals(ProfileV2(customerId = 9_000_000_123L, fullName = "Katherine Johnson", loyaltyPoints = 1_250), reencoded)
    }

    @Test
    fun encodeDefaultsLetsAnOlderReaderSeeValuesTheNewerWriterLeftAtDefault() {
        val address = Address(city = "London", postalCode = "N1")
        val written = ProfileV2(customerId = 3L, fullName = "Ada Lovelace", shippingAddress = address)

        val compact: ByteArray = protoBuf.encodeToByteArray(written)
        val explicit: ByteArray = protoBufWithDefaults.encodeToByteArray(written)

        // Only customerId (08 03), fullName (12 0C ...) and the address (42 len ...) are written.
        val addressBytes: ByteArray = protoBuf.encodeToByteArray(address)
        val expectedCompact: ByteArray = byteArrayOf(0x08, 0x03, 0x12, 0x0C) + "Ada Lovelace".encodeToByteArray() +
            byteArrayOf(0x42, addressBytes.size.toByte()) + addressBytes
        assertArrayEquals(expectedCompact, compact)
        // Field 9, varint: tag 0x48, value 1 for marketingOptIn = true.
        assertTrue(explicit.size > compact.size)
        assertEquals(listOf<Byte>(0x48, 0x01), explicit.takeLast(2))
        assertEquals(written, protoBuf.decodeFromByteArray<ProfileV2>(explicit))
        assertEquals(
            ProfileV1(customerId = 3L, displayName = "Ada Lovelace"),
            protoBuf.decodeFromByteArray<ProfileV1>(explicit),
        )
    }

    @Test
    fun encodeDefaultsRefusesANullOptionalBecauseProtobufCannotRepresentIt() {
        val withoutAddress = ProfileV2(customerId = 4L, fullName = "Hedy Lamarr")

        assertThrows(SerializationException::class.java) {
            protoBufWithDefaults.encodeToByteArray(withoutAddress)
        }
        assertEquals(withoutAddress, protoBuf.decodeFromByteArray<ProfileV2>(protoBuf.encodeToByteArray(withoutAddress)))
    }

    @Test
    fun readerKeepsTheLastValueWhenAScalarFieldRepeatsOnTheWire() {
        val first: ByteArray = protoBuf.encodeToByteArray(ProfileV1(customerId = 1L, displayName = "Draft", loyaltyPoints = 10))
        val update: ByteArray = protoBuf.encodeToByteArray(ProfileV1(customerId = 1L, displayName = "Final", loyaltyPoints = 99))

        // Concatenating two encodings is how protobuf merges a partial update into a message.
        val merged: ProfileV2 = protoBuf.decodeFromByteArray(first + update)

        assertEquals("Final", merged.fullName)
        assertEquals(99, merged.loyaltyPoints)
    }

    @Test
    fun readerRejectsAPayloadMissingAFieldItRequires() {
        val written: ByteArray = protoBuf.encodeToByteArray(ProfileV1(customerId = 44L, displayName = "Edsger Dijkstra"))

        val failure: MissingFieldException = assertThrows(MissingFieldException::class.java) {
            protoBuf.decodeFromByteArray<ProfileV3>(written)
        }

        assertEquals(listOf("accountManager"), failure.missingFields)
    }
}
