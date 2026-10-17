/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.NonEmptyList
import arrow.core.NonEmptySet
import arrow.core.None
import arrow.core.Some
import arrow.core.compareTo
import arrow.core.flatten
import arrow.core.max
import arrow.core.maxBy
import arrow.core.min
import arrow.core.minBy
import arrow.core.nonEmptyListOf
import arrow.core.nonEmptySetOf
import arrow.core.toNonEmptyListOrNone
import arrow.core.toNonEmptyListOrNull
import arrow.core.toNonEmptySetOrNull
import arrow.core.unzip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Non-empty collections (`NonEmptyList` and `NonEmptySet` construction, `head`/`tail`,
 * `coflatMap`, `min`/`max`/`minBy`/`maxBy`, `zip`, `unzip`, `flatten`,
 * `distinctBy` and the `toNonEmpty*OrNull`/`OrNone` conversions).
 *
 * An incident-response service keeps its on-call rotation in a `NonEmptyList`, so a page
 * always has someone to go to: the head is paged first and `coflatMap` derives the
 * escalation chain left at every step. Rosters loaded from configuration only become a
 * rotation once `toNonEmptyListOrNull` proves they hold someone; latency samples are
 * summarised with the total `min`/`max` that a possibly-empty list cannot offer,
 * and the tags of an incident are kept in a `NonEmptySet`.
 */
class OnCallRotationNonEmptyCollectionsTest {

    enum class Severity { LOW, HIGH, CRITICAL }

    data class Incident(
        val id: String,
        val service: String,
        val severity: Severity,
        val minutesOpen: Int,
    )

    data class Rotation(val team: String, val engineers: NonEmptyList<String>) {
        val primary: String get() = engineers.head

        val backups: List<String> get() = engineers.tail

        fun escalationChains(): NonEmptyList<String> =
            engineers.coflatMap { remaining -> remaining.joinToString(" -> ") }

        fun handOver(): Rotation =
            (backups + primary).toNonEmptyListOrNull()!!.let { Rotation(team, it) }
    }

    data class LatencySummary(val fastestMs: Int, val slowestMs: Int, val meanMs: Int)

    private fun rotationFrom(team: String, configured: List<String>): Rotation? =
        configured.map(String::trim)
            .filter(String::isNotEmpty)
            .toNonEmptyListOrNull()
            ?.let { Rotation(team, it) }

    private fun summarise(samples: NonEmptyList<Int>): LatencySummary {
        return LatencySummary(samples.min(), samples.max(), samples.sum() / samples.size)
    }

    @Test
    fun pagesThePrimaryAndDerivesEscalationChains() {
        val rotation: Rotation = rotationFrom("payments", listOf(" alice ", "bob", "", "carol"))!!

        assertEquals("alice", rotation.primary)
        assertEquals(listOf("bob", "carol"), rotation.backups)
        assertEquals(
            nonEmptyListOf("alice -> bob -> carol", "bob -> carol", "carol"),
            rotation.escalationChains(),
        )

        val nextWeek: Rotation = rotation.handOver()
        assertEquals(nonEmptyListOf("bob", "carol", "alice"), nextWeek.engineers)
        assertEquals("bob", nextWeek.primary)

        val solo: Rotation = rotationFrom("search", listOf("dave"))!!
        assertEquals("dave", solo.handOver().primary)
        assertEquals(nonEmptyListOf("dave"), solo.escalationChains())
    }

    @Test
    fun rejectsRostersWithNobodyOnThem() {
        assertNull(rotationFrom("billing", listOf(" ", "")))
        assertEquals(None, emptyList<String>().toNonEmptyListOrNone())
        assertEquals(Some(nonEmptyListOf("erin")), listOf("erin").toNonEmptyListOrNone())
        assertNull(emptySet<String>().toNonEmptySetOrNull())
    }

    @Test
    fun summarisesLatencyWithoutAnEmptyCase() {
        val samples: NonEmptyList<Int> = nonEmptyListOf(120, 85, 300, 95)

        assertEquals(
            LatencySummary(fastestMs = 85, slowestMs = 300, meanMs = 150),
            summarise(samples),
        )
        assertEquals(LatencySummary(42, 42, 42), summarise(nonEmptyListOf(42)))

        val extended: NonEmptyList<Int> = samples + listOf(10, 400)
        assertEquals(6, extended.size)
        assertEquals(LatencySummary(10, 400, 168), summarise(extended))
    }

    @Test
    fun picksTheWorstIncidentAndGroupsOwners() {
        val open: NonEmptyList<Incident> = nonEmptyListOf(
            Incident("INC-1", "checkout", Severity.HIGH, 40),
            Incident("INC-2", "search", Severity.CRITICAL, 5),
            Incident("INC-3", "checkout", Severity.LOW, 90),
        )

        assertEquals("INC-2", open.maxBy { it.severity }.id)
        assertEquals("INC-2", open.minBy { it.minutesOpen }.id)
        assertEquals(
            nonEmptyListOf("checkout", "search"),
            open.distinctBy { it.service }.map { it.service },
        )

        val owners: NonEmptyList<String> = nonEmptyListOf("alice", "dave", "bob")
        val assignments: NonEmptyList<String> =
            open.zip(owners) { incident, owner -> "${incident.id}@$owner" }
        assertEquals(nonEmptyListOf("INC-1@alice", "INC-2@dave", "INC-3@bob"), assignments)

        val (ids: NonEmptyList<String>, minutes: NonEmptyList<Int>) =
            open.map { it.id to it.minutesOpen }.unzip()
        assertEquals(nonEmptyListOf("INC-1", "INC-2", "INC-3"), ids)
        assertEquals(135, minutes.sum())
    }

    @Test
    fun mergesTeamRostersAndTagsWithoutLosingNonEmptiness() {
        val teams: NonEmptyList<Rotation> = nonEmptyListOf(
            Rotation("payments", nonEmptyListOf("alice", "bob")),
            Rotation("search", nonEmptyListOf("dave")),
            Rotation("platform", nonEmptyListOf("bob", "erin")),
        )

        val everyone: NonEmptyList<String> = teams.map { it.engineers }.flatten()
        assertEquals(nonEmptyListOf("alice", "bob", "dave", "bob", "erin"), everyone)
        assertEquals(nonEmptyListOf("alice", "bob", "dave", "erin"), everyone.distinct())
        assertEquals(everyone, teams.flatMap { it.engineers })

        val uniqueEngineers: NonEmptySet<String> = everyone.toNonEmptySet()
        assertEquals(4, uniqueEngineers.size)
        assertEquals("alice", uniqueEngineers.head)

        val tags: NonEmptySet<String> = nonEmptySetOf("database", "latency", "database")
        assertEquals(2, tags.size)
        val escalated: NonEmptySet<String> = tags + "sev1"
        assertEquals(setOf("database", "latency", "sev1"), escalated.toSet())
        assertTrue("sev1" in escalated)
        assertEquals(nonEmptyListOf("DATABASE", "LATENCY"), tags.map(String::uppercase))
    }

    @Test
    fun ordersRunbookVersionsLexicographically() {
        val deployed: NonEmptyList<Int> = nonEmptyListOf(2, 4, 1)
        val candidate: NonEmptyList<Int> = nonEmptyListOf(2, 10)
        val hotfix: NonEmptyList<Int> = nonEmptyListOf(2, 4, 1, 1)

        assertTrue(deployed < candidate)
        assertTrue(deployed < hotfix)
        assertTrue(candidate > hotfix)
        assertEquals(0, deployed.compareTo(nonEmptyListOf(2, 4, 1)))
    }
}
