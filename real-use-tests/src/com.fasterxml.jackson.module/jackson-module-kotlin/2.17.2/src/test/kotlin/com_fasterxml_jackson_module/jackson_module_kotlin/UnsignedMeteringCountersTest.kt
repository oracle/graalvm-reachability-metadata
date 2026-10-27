/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Kotlin unsigned-number JSON binding for network-metering counters.
 * A billing worker reads cumulative byte counters beyond the signed integer limits,
 * archives the readings losslessly, and publishes the usage between consecutive readings.
 */
public class UnsignedMeteringCountersTest {

    public data class ChannelCounter(val channelId: UInt, val bytes: ULong)

    public data class MeterReading(val meterId: String, val counters: List<ChannelCounter>)

    public data class UsageReport(
        val meterId: String,
        val counters: List<ChannelCounter>,
        val totalBytes: ULong,
    )

    @Test
    @Timeout(60)
    public fun preservesUnsignedReadingsAndBillsOnlyTheCounterIncrements(): Unit {
        val module: KotlinModule = KotlinModule.Builder().build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val readingsJson: String = """
            [
              {
                "meterId": "edge-7",
                "counters": [
                  {"channelId": 4294967295, "bytes": 18446744073709547519},
                  {"channelId": 2147483648, "bytes": 9223372036854775808}
                ]
              },
              {
                "meterId": "edge-7",
                "counters": [
                  {"channelId": 4294967295, "bytes": 18446744073709551615},
                  {"channelId": 2147483648, "bytes": 9223372036854777856}
                ]
              }
            ]
        """.trimIndent()

        val readings: List<MeterReading> = mapper.readValue(readingsJson)
        assertEquals(2, readings.size)
        assertEquals("edge-7", readings[0].meterId)
        assertEquals(
            listOf(
                ChannelCounter(UInt.MAX_VALUE, 18_446_744_073_709_547_519uL),
                ChannelCounter(2_147_483_648u, 9_223_372_036_854_775_808uL),
            ),
            readings[0].counters,
        )
        assertEquals(
            listOf(
                ChannelCounter(UInt.MAX_VALUE, ULong.MAX_VALUE),
                ChannelCounter(2_147_483_648u, 9_223_372_036_854_777_856uL),
            ),
            readings[1].counters,
        )

        val archiveJson: String = mapper.writeValueAsString(readings)
        val archive: JsonNode = mapper.readTree(archiveJson)
        assertEquals(mapper.readTree(readingsJson), archive)
        val restoredReadings: List<MeterReading> = mapper.readValue(archiveJson)
        assertEquals(readings, restoredReadings)

        val previous: MeterReading = restoredReadings[0]
        val current: MeterReading = restoredReadings[1]
        require(previous.meterId == current.meterId)
        val baseline: Map<UInt, ULong> = previous.counters.associate { counter: ChannelCounter ->
            counter.channelId to counter.bytes
        }
        val increments: List<ChannelCounter> = current.counters.map { counter: ChannelCounter ->
            val previousBytes: ULong = baseline.getValue(counter.channelId)
            require(counter.bytes >= previousBytes) { "A reset counter needs a new billing baseline" }
            ChannelCounter(counter.channelId, counter.bytes - previousBytes)
        }
        val report: UsageReport = UsageReport(
            current.meterId,
            increments,
            increments.fold(0uL) { total: ULong, counter: ChannelCounter -> total + counter.bytes },
        )
        assertEquals(
            listOf(ChannelCounter(UInt.MAX_VALUE, 4_096uL), ChannelCounter(2_147_483_648u, 2_048uL)),
            report.counters,
        )
        assertEquals(6_144uL, report.totalBytes)

        val reportJson: String = mapper.writeValueAsString(report)
        val expectedReport: JsonNode = mapper.readTree(
            """
                {
                  "meterId": "edge-7",
                  "counters": [
                    {"channelId": 4294967295, "bytes": 4096},
                    {"channelId": 2147483648, "bytes": 2048}
                  ],
                  "totalBytes": 6144
                }
            """.trimIndent(),
        )
        assertEquals(expectedReport, mapper.readTree(reportJson))
        val receivedReport: UsageReport = mapper.readValue(reportJson)
        assertEquals(report, receivedReport)
    }
}
