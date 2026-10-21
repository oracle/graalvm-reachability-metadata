/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import java.util.UUID
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborConfiguration
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.cbor.ObjectTags
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises `Cbor` instance configuration: instances derived from a shared base with
 * `Cbor(from = ...) { }`, the resulting `CborConfiguration`, per-instance label and object-tag
 * policies, and a storage layer written against the format-agnostic `BinaryFormat` interface.
 *
 * A document archive keeps one base format for its own records and derives a compact,
 * label-keyed format for constrained devices and an untagged format for a legacy partner.
 */
@OptIn(ExperimentalSerializationApi::class)
public class ArchiveChannelConfigurationCborTest {

    /** Writes record identifiers as their canonical text form. */
    public object UuidAsStringSerializer : KSerializer<UUID> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("archive.Uuid", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: UUID) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    }

    @Serializable
    public data class CatalogEntry(
        @CborLabel(1) @SerialName("id") val id: Int,
        @CborLabel(2) @SerialName("title") val title: String,
    )

    @Serializable
    @ObjectTags(SELF_DESCRIBED_CBOR_TAG)
    public data class ArchiveRecord(
        @Contextual val recordId: UUID,
        val entries: List<CatalogEntry>,
        val retentionDays: Int = 365,
    )

    /** Persists values under string keys through any binary format the archive hands it. */
    public class DocumentStore(public val format: BinaryFormat) {
        private val blobs: MutableMap<String, ByteArray> = linkedMapOf()

        public inline fun <reified T> put(key: String, value: T): ByteArray {
            val blob: ByteArray = format.encodeToByteArray(value)
            store(key, blob)
            return blob
        }

        public inline fun <reified T> get(key: String): T = format.decodeFromByteArray(load(key))

        public fun store(key: String, blob: ByteArray) {
            blobs[key] = blob.copyOf()
        }

        public fun load(key: String): ByteArray =
            requireNotNull(blobs[key]) { "No document stored under $key" }.copyOf()
    }

    private val archiveModule: SerializersModule = SerializersModule {
        contextual(UuidAsStringSerializer)
    }

    private val archiveFormat: Cbor = Cbor {
        serializersModule = archiveModule
        ignoreUnknownKeys = true
        encodeObjectTags = true
        verifyObjectTags = true
    }

    private val deviceFormat: Cbor = Cbor(from = archiveFormat) {
        preferCborLabelsOverNames = true
        useDefiniteLengthEncoding = true
    }

    private val legacyPartnerFormat: Cbor = Cbor(from = archiveFormat) {
        encodeObjectTags = false
        verifyObjectTags = false
    }

    private val sampleRecord: ArchiveRecord = ArchiveRecord(
        recordId = UUID.fromString("5f0c3a4e-8d21-4b7a-9c3e-1a2b3c4d5e6f"),
        entries = listOf(CatalogEntry(7, "ok"), CatalogEntry(8, "map")),
    )

    @Test
    fun derivedFormatsInheritTheBaseConfigurationAndOverrideOnlyWhatTheySet() {
        val base: CborConfiguration = archiveFormat.configuration
        val device: CborConfiguration = deviceFormat.configuration
        val legacy: CborConfiguration = legacyPartnerFormat.configuration

        assertTrue(base.ignoreUnknownKeys)
        assertFalse(base.preferCborLabelsOverNames)
        assertFalse(base.useDefiniteLengthEncoding)
        assertTrue(base.encodeObjectTags)
        assertTrue(base.verifyObjectTags)
        assertFalse(Cbor.Default.configuration.encodeObjectTags)

        assertTrue(device.ignoreUnknownKeys)
        assertTrue(device.preferCborLabelsOverNames)
        assertTrue(device.useDefiniteLengthEncoding)
        assertTrue(device.encodeObjectTags)
        assertEquals(base.encodeDefaults, device.encodeDefaults)

        assertTrue(legacy.ignoreUnknownKeys)
        assertFalse(legacy.encodeObjectTags)
        assertFalse(legacy.verifyObjectTags)
        assertFalse(legacy.preferCborLabelsOverNames)

        assertSame(archiveModule, deviceFormat.serializersModule)
        assertNotNull(legacyPartnerFormat.serializersModule.getContextual(UUID::class))

        val coseDerived: Cbor = Cbor(from = Cbor.CoseCompliant) { useDefiniteLengthEncoding = false }
        assertTrue(coseDerived.configuration.preferCborLabelsOverNames)
        assertFalse(coseDerived.configuration.useDefiniteLengthEncoding)
        assertTrue(Cbor.CoseCompliant.configuration.useDefiniteLengthEncoding)
    }

    @Test
    fun deviceFormatKeysPropertiesByLabelWhileTheBaseFormatKeysThemByName() {
        val entry: CatalogEntry = CatalogEntry(7, "ok")

        val byName: ByteArray = archiveFormat.encodeToByteArray(entry)
        val byLabel: ByteArray = deviceFormat.encodeToByteArray(entry)

        assertArrayEquals(hex("bf62696407657469746c65626f6bff"), byName)
        assertArrayEquals(hex("a2010702626f6b"), byLabel)
        assertTrue(byLabel.size < byName.size)

        assertEquals(entry, deviceFormat.decodeFromByteArray<CatalogEntry>(byLabel))
        assertEquals(entry, archiveFormat.decodeFromByteArray<CatalogEntry>(byName))
        assertEquals(entry, archiveFormat.decodeFromByteArray<CatalogEntry>(byLabel))
        assertEquals(entry, deviceFormat.decodeFromByteArray<CatalogEntry>(byName))
    }

    @Test
    fun objectTagsAreWrittenAndVerifiedOnlyWhereTheChannelAsksForThem() {
        val tagged: ByteArray = archiveFormat.encodeToByteArray(sampleRecord)
        val untagged: ByteArray = legacyPartnerFormat.encodeToByteArray(sampleRecord)

        assertArrayEquals(SELF_DESCRIBED_PREFIX, tagged.copyOfRange(0, SELF_DESCRIBED_PREFIX.size))
        assertArrayEquals(untagged, tagged.copyOfRange(SELF_DESCRIBED_PREFIX.size, tagged.size))

        assertEquals(sampleRecord, archiveFormat.decodeFromByteArray<ArchiveRecord>(tagged))
        assertEquals(sampleRecord, legacyPartnerFormat.decodeFromByteArray<ArchiveRecord>(untagged))
        assertEquals(sampleRecord, legacyPartnerFormat.decodeFromByteArray<ArchiveRecord>(tagged))
        assertThrows(SerializationException::class.java) {
            archiveFormat.decodeFromByteArray<ArchiveRecord>(untagged)
        }
    }

    @Test
    fun documentStoreWorksUnchangedOverEveryConfiguredFormat() {
        val stores: Map<String, DocumentStore> = mapOf(
            "archive" to DocumentStore(archiveFormat),
            "device" to DocumentStore(deviceFormat),
            "legacy" to DocumentStore(legacyPartnerFormat),
        )

        val blobs: Map<String, ByteArray> = stores.mapValues { (_, store: DocumentStore) ->
            store.put("record-1", sampleRecord)
        }
        stores.values.forEach { store: DocumentStore ->
            store.put("catalog", sampleRecord.entries)
        }

        stores.values.forEach { store: DocumentStore ->
            assertEquals(sampleRecord, store.get<ArchiveRecord>("record-1"))
            assertEquals(sampleRecord.entries, store.get<List<CatalogEntry>>("catalog"))
        }
        assertEquals(3, blobs.values.map { blob: ByteArray -> blob.toList() }.toSet().size)
        assertEquals(0xa2, blobs.getValue("device")[SELF_DESCRIBED_PREFIX.size].toInt() and 0xff)

        val archiveStore: DocumentStore = stores.getValue("archive")
        archiveStore.store("from-partner", stores.getValue("legacy").load("record-1"))
        assertThrows(SerializationException::class.java) {
            archiveStore.get<ArchiveRecord>("from-partner")
        }
        val error: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            archiveStore.get<ArchiveRecord>("missing")
        }
        assertEquals("No document stored under missing", error.message)
    }

    private fun hex(text: String): ByteArray =
        text.chunked(2).map { pair: String -> pair.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val SELF_DESCRIBED_CBOR_TAG: ULong = 55799u
        val SELF_DESCRIBED_PREFIX: ByteArray =
            byteArrayOf(0xd9.toByte(), 0xd9.toByte(), 0xf7.toByte())
    }
}
