/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Tuple4
import arrow.core.Tuple5
import arrow.core.Tuple9
import arrow.core.compareTo
import arrow.core.plus
import arrow.core.sort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tuple product types and lexicographic ordering (`Tuple4` to `Tuple9` built with `plus`,
 * destructuring, `copy`, equality as map keys, the tuple `compareTo` extensions and `sort`).
 *
 * An incident desk ranks open incidents by a composite key that grows one column at a time
 * (severity, customer tier, opening minute, service, id) and compares those keys with Arrow's
 * lexicographic `compareTo`. Incidents are bucketed by a `Tuple4` routing key, a wide `Tuple9`
 * export row is destructured for the shift report, and escalation thresholds are normalised
 * with `sort`.
 */
class IncidentTriageTupleOrderingTest {

    data class Incident(
        val id: String,
        val severity: Int,
        val customerTier: Int,
        val openedAtMinute: Long,
        val service: String,
        val region: String,
    )

    private val incidents: List<Incident> = listOf(
        Incident("INC-7", 2, 1, 40L, "billing", "eu"),
        Incident("INC-3", 1, 2, 15L, "search", "us"),
        Incident("INC-9", 1, 1, 30L, "checkout", "eu"),
        Incident("INC-1", 1, 1, 30L, "billing", "us"),
        Incident("INC-5", 2, 1, 40L, "billing", "eu"),
        Incident("INC-4", 3, 3, 5L, "search", "us"),
    )

    private fun rankKey(incident: Incident): Tuple5<Int, Int, Long, String, String> =
        (incident.severity to incident.customerTier) +
            incident.openedAtMinute +
            incident.service +
            incident.id

    private fun routingKey(incident: Incident): Tuple4<String, String, Int, Boolean> =
        Triple(incident.region, incident.service, incident.severity) + (incident.customerTier == 1)

    private fun triageQueue(open: List<Incident>): List<String> =
        open.sortedWith { left: Incident, right: Incident -> rankKey(left).compareTo(rankKey(right)) }
            .map(Incident::id)

    @Test
    fun ranksIncidentsByLexicographicCompositeKey() {
        assertEquals(listOf("INC-1", "INC-9", "INC-3", "INC-5", "INC-7", "INC-4"), triageQueue(incidents))

        val first: Tuple5<Int, Int, Long, String, String> = rankKey(incidents[3])
        val second: Tuple5<Int, Int, Long, String, String> = rankKey(incidents[2])
        assertTrue(first < second)
        assertTrue(second > first)
        assertEquals(0, first.compareTo(first.copy()))

        val (severity: Int, tier: Int, openedAt: Long, service: String, id: String) = first
        assertEquals(Tuple5(1, 1, 30L, "billing", "INC-1"), Tuple5(severity, tier, openedAt, service, id))
        assertEquals("(1, 1, 30, billing, INC-1)", first.toString())
    }

    @Test
    fun comparesPairsAndTriplesBeforeTheyGrowIntoTuples() {
        val nightShift: Pair<Int, String> = 2 to "eu"
        val dayShift: Pair<Int, String> = 2 to "us"
        assertTrue(nightShift < dayShift)

        val olderWindow: Triple<Int, Int, Int> = Triple(2026, 10, 3)
        val newerWindow: Triple<Int, Int, Int> = Triple(2026, 9, 30)
        assertTrue(olderWindow > newerWindow)
        assertEquals(0, olderWindow.compareTo(Triple(2026, 10, 3)))
    }

    @Test
    fun bucketsIncidentsByTupleRoutingKey() {
        val buckets: Map<Tuple4<String, String, Int, Boolean>, List<String>> =
            incidents.groupBy(::routingKey, Incident::id)

        assertEquals(5, buckets.size)
        assertEquals(listOf("INC-7", "INC-5"), buckets[Tuple4("eu", "billing", 2, true)])
        assertEquals(listOf("INC-1"), buckets[Tuple4("us", "billing", 1, true)])
        assertEquals(null, buckets[Tuple4("eu", "billing", 2, false)])

        val reassigned: Tuple4<String, String, Int, Boolean> =
            routingKey(incidents[1]).copy(first = "eu", fourth = true)
        assertEquals(Tuple4("eu", "search", 1, true), reassigned)
    }

    @Test
    fun exportsShiftReportRowAsTuple9() {
        val incident: Incident = incidents.first { it.id == "INC-9" }
        val row: Tuple9<String, Int, Int, Long, String, String, Boolean, String, Int> =
            routingKey(incident).let { (region: String, service: String, severity: Int, priority: Boolean) ->
                (incident.id to severity) +
                    incident.customerTier +
                    incident.openedAtMinute +
                    service +
                    region +
                    priority +
                    "night" +
                    triageQueue(incidents).indexOf(incident.id)
            }

        val (id, severity, tier, openedAt, service, region, priority, shift, position) = row
        val csv: String = listOf(id, severity, tier, openedAt, service, region, priority, shift, position)
            .joinToString(",")
        assertEquals("INC-9,1,1,30,checkout,eu,true,night,1", csv)
        assertEquals(row, Tuple9("INC-9", 1, 1, 30L, "checkout", "eu", true, "night", 1))
        assertTrue(row < row.copy(ninth = 2))
    }

    @Test
    fun normalisesEscalationThresholdsWithSort() {
        assertEquals(Triple(5, 15, 30), sort(30, 5, 15))
        assertEquals(Pair(10L, 60L), sort(60L, 10L))
        assertEquals(listOf("db", "edge", "mq", "web"), sort("web", "mq", "db", "edge"))

        val byLength: Comparator<String> = compareBy(String::length)
        assertEquals(Triple("mq", "web", "edge"), sort("edge", "mq", "web", byLength))
    }
}
