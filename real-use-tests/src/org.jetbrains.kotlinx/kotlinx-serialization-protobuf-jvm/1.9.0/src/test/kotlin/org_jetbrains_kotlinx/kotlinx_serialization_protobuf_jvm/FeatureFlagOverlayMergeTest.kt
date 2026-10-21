/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/**
 * Decoding of concatenated encoded messages by `ProtoBuf` as one merged message.
 *
 * A feature-flag service keeps the configuration of each environment as a base protobuf blob and
 * appends the encoded overlay of every rollout step to it, the merge-by-concatenation idiom of
 * protobuf. Reading the combined blob must give the last value of every scalar field that a later
 * overlay sets, keep base values that no overlay touches, and append the repeated fields of all
 * overlays in order.
 */
@OptIn(ExperimentalSerializationApi::class)
class FeatureFlagOverlayMergeTest {

    @Serializable
    data class FlagConfig(
        @ProtoNumber(1) val environment: String = "",
        @ProtoNumber(2) val rolloutPercent: Int = 0,
        @ProtoNumber(3) val enabledFlags: List<String> = emptyList(),
        @ProtoNumber(4) val owner: String = "",
        @ProtoNumber(5) val revision: Long = 0L,
    )

    /** Appends encoded overlays to a base blob the way the configuration store persists them. */
    class OverlayLog(private val protoBuf: ProtoBuf, base: FlagConfig) {
        private val blob: ByteArrayOutputStream = ByteArrayOutputStream()

        init {
            append(base)
        }

        fun append(overlay: FlagConfig) {
            blob.write(protoBuf.encodeToByteArray(overlay))
        }

        fun current(): FlagConfig = protoBuf.decodeFromByteArray(blob.toByteArray())
    }

    private val protoBuf: ProtoBuf = ProtoBuf

    @Test
    fun laterOverlaysOverrideScalarsAndKeepUntouchedFields() {
        val log = OverlayLog(
            protoBuf,
            FlagConfig(environment = "staging", rolloutPercent = 5, owner = "payments", revision = 1L),
        )
        log.append(FlagConfig(rolloutPercent = 25, revision = 2L))
        log.append(FlagConfig(rolloutPercent = 50, revision = 3L))

        assertEquals(
            FlagConfig(environment = "staging", rolloutPercent = 50, owner = "payments", revision = 3L),
            log.current(),
        )
    }

    @Test
    fun repeatedFieldsOfEveryOverlayAreAppendedInOrder() {
        val log = OverlayLog(
            protoBuf,
            FlagConfig(environment = "production", enabledFlags = listOf("dark-mode"), revision = 10L),
        )
        log.append(FlagConfig(enabledFlags = listOf("new-checkout", "fast-search"), revision = 11L))
        log.append(FlagConfig(owner = "growth", enabledFlags = listOf("referrals"), revision = 12L))

        val merged: FlagConfig = log.current()

        assertEquals(listOf("dark-mode", "new-checkout", "fast-search", "referrals"), merged.enabledFlags)
        assertEquals("production", merged.environment)
        assertEquals("growth", merged.owner)
        assertEquals(12L, merged.revision)
    }

    @Test
    fun mergedBlobMatchesManuallyConcatenatedBytes() {
        val base = FlagConfig(environment = "qa", rolloutPercent = 10, revision = 1L)
        val overlay = FlagConfig(rolloutPercent = 100, enabledFlags = listOf("beta-banner"), revision = 2L)
        val concatenated: ByteArray = protoBuf.encodeToByteArray(base) + protoBuf.encodeToByteArray(overlay)

        val log = OverlayLog(protoBuf, base)
        log.append(overlay)

        val expected = FlagConfig(
            environment = "qa",
            rolloutPercent = 100,
            enabledFlags = listOf("beta-banner"),
            revision = 2L,
        )
        assertEquals(expected, protoBuf.decodeFromByteArray<FlagConfig>(concatenated))
        assertEquals(expected, log.current())
    }
}
