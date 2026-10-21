/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Encode-side field presence of defaulted properties in `ProtoBuf`: `encodeDefaults`, `@EncodeDefault` and `@Transient`.
 *
 * A notification service stores per-user settings as protobuf blobs in a text-only key-value store,
 * so it ships them as hex strings. Compact blobs leave out every setting the user never changed,
 * except the schema revision that readers dispatch on; an export for the analytics team writes every
 * default explicitly, but never the quiet-hours flag while it is off; and the in-memory view counter
 * never leaves the process.
 */
@OptIn(ExperimentalSerializationApi::class)
class NotificationSettingsDefaultPresenceTest {

    @Serializable
    data class NotificationSettings(
        @ProtoNumber(1) val userId: Long,
        @ProtoNumber(2) val digestHour: Int = 8,
        @ProtoNumber(3) val channels: List<String> = listOf("email"),
        @ProtoNumber(4) val locale: String = "en",
        @ProtoNumber(5) @EncodeDefault(EncodeDefault.Mode.ALWAYS) val schemaRevision: Int = 3,
        @ProtoNumber(6) @EncodeDefault(EncodeDefault.Mode.NEVER) val quietHours: Boolean = false,
        @Transient val viewsThisSession: Int = 0,
    ) {
        fun deliversBy(channel: String): Boolean = channel in channels
    }

    /** What an analytics reader that only knows the revision and the quiet-hours flag decodes. */
    @Serializable
    data class RevisionProbe(
        @ProtoNumber(5) val schemaRevision: Int = 0,
        @ProtoNumber(6) val quietHours: Boolean = true,
    )

    private val compact: ProtoBuf = ProtoBuf

    private val export: ProtoBuf = ProtoBuf { encodeDefaults = true }

    @Test
    fun compactBlobKeepsOnlyChangedSettingsAndTheAlwaysEncodedRevision() {
        val settings = NotificationSettings(userId = 42L)

        val bytes: ByteArray = compact.encodeToByteArray(settings)

        assertArrayEquals(byteArrayOf(0x08, 0x2A, 0x28, 0x03), bytes)
        val restored: NotificationSettings = compact.decodeFromByteArray(bytes)
        assertEquals(settings, restored)
        assertEquals(8, restored.digestHour)
        assertEquals("en", restored.locale)
        assertEquals(true, restored.deliversBy("email"))
    }

    @Test
    fun compactBlobWritesOnlyTheSettingsTheUserChanged() {
        val settings = NotificationSettings(userId = 42L, locale = "de", quietHours = true)

        val bytes: ByteArray = compact.encodeToByteArray(settings)

        val expected: ByteArray = byteArrayOf(0x08, 0x2A, 0x22, 0x02) + "de".encodeToByteArray() +
            byteArrayOf(0x28, 0x03, 0x30, 0x01)
        assertArrayEquals(expected, bytes)
        assertEquals(settings, compact.decodeFromByteArray<NotificationSettings>(bytes))
    }

    @Test
    fun exportWritesEveryDefaultExceptTheNeverEncodedFlag() {
        val settings = NotificationSettings(userId = 42L)

        val bytes: ByteArray = export.encodeToByteArray(settings)

        val expected: ByteArray = byteArrayOf(0x08, 0x2A, 0x10, 0x08, 0x1A, 0x05) + "email".encodeToByteArray() +
            byteArrayOf(0x22, 0x02) + "en".encodeToByteArray() + byteArrayOf(0x28, 0x03)
        assertArrayEquals(expected, bytes)
        assertEquals(settings, export.decodeFromByteArray<NotificationSettings>(bytes))
        assertEquals(RevisionProbe(schemaRevision = 3, quietHours = true), export.decodeFromByteArray<RevisionProbe>(bytes))

        val quiet: ByteArray = export.encodeToByteArray(settings.copy(quietHours = true))
        assertEquals(RevisionProbe(schemaRevision = 3, quietHours = true), export.decodeFromByteArray<RevisionProbe>(quiet))
        assertArrayEquals(bytes + byteArrayOf(0x30, 0x01), quiet)
    }

    @Test
    fun transientSessionCounterNeverReachesTheStore() {
        val active = NotificationSettings(userId = 42L, channels = listOf("push", "sms"), viewsThisSession = 17)

        val bytes: ByteArray = export.encodeToByteArray(active)

        assertArrayEquals(export.encodeToByteArray(active.copy(viewsThisSession = 0)), bytes)
        val restored: NotificationSettings = export.decodeFromByteArray(bytes)
        assertEquals(0, restored.viewsThisSession)
        assertEquals(listOf("push", "sms"), restored.channels)
        assertEquals(false, restored.deliversBy("email"))
    }

    @Test
    fun hexStringRoundTripsThroughTheTextStore() {
        val store: MutableMap<String, String> = mutableMapOf()
        val settings = NotificationSettings(userId = 7L, digestHour = 18)

        store["settings:7"] = compact.encodeToHexString(settings)

        assertEquals("080710122803", store.getValue("settings:7"))
        val loaded: NotificationSettings = compact.decodeFromHexString(store.getValue("settings:7"))
        assertEquals(settings, loaded)
        assertEquals(18, loaded.digestHour)
        assertEquals(NotificationSettings(userId = 7L), compact.decodeFromHexString<NotificationSettings>("08072803"))
    }
}
