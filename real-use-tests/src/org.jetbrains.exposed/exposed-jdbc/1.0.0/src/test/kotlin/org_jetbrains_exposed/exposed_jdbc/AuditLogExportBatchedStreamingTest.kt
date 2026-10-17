/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.SizedIterable
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.emptySized
import org.jetbrains.exposed.v1.jdbc.mapLazy
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

/**
 * Batched result streaming: a nightly audit-log export walks the log in keyset batches with
 * `fetchBatchedResults`, streams rows lazily through `fetchSize` and `mapLazy`, and falls back to
 * in-memory `SizedIterable`s when there is nothing to read from the database.
 */
class AuditLogExportBatchedStreamingTest {

    object AuditEvents : Table("audit_events") {
        val id = long("id").autoIncrement()
        val actor = varchar("actor", 32)
        val severity = varchar("severity", 8)
        val action = varchar("action", 64)

        override val primaryKey = PrimaryKey(id)
    }

    /** A reference table without an auto-incrementing column, so it cannot be keyset-batched. */
    object Severities : Table("severities") {
        val code = varchar("code", 8)
        val rank = integer("rank")

        override val primaryKey = PrimaryKey(code)
    }

    data class ExportLine(val id: Long, val actor: String, val action: String) {
        fun render(): String = "$id|$actor|$action"
    }

    /** Collects the size of every batch so the export can report how it paged the log. */
    class BatchedExport {
        val batchSizes: MutableList<Int> = mutableListOf()
        val lines: MutableList<ExportLine> = mutableListOf()

        fun accept(batch: Iterable<ExportLine>) {
            val rows: List<ExportLine> = batch.toList()
            batchSizes += rows.size
            lines += rows
        }
    }

    @Test
    fun exportsTheAuditLogInKeysetBatchesAndLazyStreams() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:audit_export;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )

        val seeded: List<Triple<String, String, String>> = (1..23).map { n: Int ->
            val severity: String = when {
                n % 5 == 0 -> "ERROR"
                n % 2 == 0 -> "WARN"
                else -> "INFO"
            }
            Triple("user${n % 4}", severity, "action-$n")
        }

        transaction(database) {
            SchemaUtils.create(AuditEvents, Severities)
            AuditEvents.batchInsert(seeded) { (actor: String, severity: String, action: String) ->
                this[AuditEvents.actor] = actor
                this[AuditEvents.severity] = severity
                this[AuditEvents.action] = action
            }
            Severities.batchInsert(listOf("INFO" to 1, "WARN" to 2, "ERROR" to 3)) { (code: String, rank: Int) ->
                this[Severities.code] = code
                this[Severities.rank] = rank
            }
        }

        transaction(database) {
            val toLine: (ResultRow) -> ExportLine = { row ->
                ExportLine(row[AuditEvents.id], row[AuditEvents.actor], row[AuditEvents.action])
            }

            val fullExport = BatchedExport()
            AuditEvents.selectAll()
                .fetchBatchedResults(batchSize = 5)
                .forEach { batch: Iterable<ResultRow> -> fullExport.accept(batch.map(toLine)) }

            assertThat(fullExport.batchSizes).containsExactly(5, 5, 5, 5, 3)
            assertThat(fullExport.lines.map(ExportLine::id)).isEqualTo((1L..23L).toList())
            assertThat(fullExport.lines.first().render()).isEqualTo("1|user1|action-1")
            assertThat(fullExport.lines.last().render()).isEqualTo("23|user3|action-23")

            val alerting: List<String> = listOf("WARN", "ERROR")
            val alertExport = BatchedExport()
            AuditEvents.selectAll()
                .where { AuditEvents.severity inList alerting }
                .fetchBatchedResults(batchSize = 4, sortOrder = SortOrder.DESC)
                .forEach { batch -> alertExport.accept(batch.map(toLine)) }

            val expectedAlertIds: List<Long> = seeded.indices
                .filter { index: Int -> seeded[index].second in alerting }
                .map { index: Int -> index + 1L }
                .reversed()
            assertThat(expectedAlertIds).hasSize(13)
            assertThat(alertExport.lines.map(ExportLine::id)).isEqualTo(expectedAlertIds)
            assertThat(alertExport.batchSizes).containsExactly(4, 4, 4, 1)

            val nothingToExport = BatchedExport()
            AuditEvents.selectAll()
                .where { AuditEvents.actor eq "nobody" }
                .fetchBatchedResults(batchSize = 10)
                .forEach { batch -> nothingToExport.accept(batch.map(toLine)) }
            assertThat(nothingToExport.batchSizes).isEmpty()
        }

        transaction(database) {
            val errors = AuditEvents.selectAll()
                .where { AuditEvents.severity eq "ERROR" }
                .orderBy(AuditEvents.id)
                .fetchSize(2)
            assertThat(errors.fetchSize).isEqualTo(2)
            assertThat(errors.count()).isEqualTo(4L)
            assertThat(errors.empty()).isFalse()

            val streamed: SizedIterable<String> = errors.mapLazy { row -> "${row[AuditEvents.id]}:${row[AuditEvents.action]}" }
            assertThat(streamed.count()).isEqualTo(4L)
            assertThat(streamed.toList()).containsExactly("5:action-5", "10:action-10", "15:action-15", "20:action-20")
            assertThat(streamed.limit(2).toList()).containsExactly("5:action-5", "10:action-10")
            assertThat(streamed.offset(3).toList()).containsExactly("20:action-20")

            val firstTwoFromIterator: MutableList<Long> = mutableListOf()
            val iterator: Iterator<ResultRow> = errors.copy().iterator()
            while (iterator.hasNext() && firstTwoFromIterator.size < 2) {
                firstTwoFromIterator += iterator.next()[AuditEvents.id]
            }
            assertThat(firstTwoFromIterator).containsExactly(5L, 10L)

            val narrowed = errors.copy().andWhere { AuditEvents.actor eq "user0" }
            assertThat(narrowed.map { row -> row[AuditEvents.id] }).containsExactly(20L)
            assertThat(errors.count()).isEqualTo(4L)
        }

        transaction(database) {
            assertThatThrownBy {
                AuditEvents.selectAll().limit(3).fetchBatchedResults(batchSize = 2).toList()
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("LIMIT")
            assertThatThrownBy {
                AuditEvents.selectAll().orderBy(AuditEvents.actor).fetchBatchedResults(batchSize = 2).toList()
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("ORDER BY")
            assertThatThrownBy {
                AuditEvents.selectAll().fetchBatchedResults(batchSize = 0).toList()
            }.isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy {
                Severities.selectAll().fetchBatchedResults(batchSize = 2).toList()
            }.isInstanceOf(UnsupportedOperationException::class.java)
                .hasMessageContaining("auto-incrementing")
        }

        val pendingOffline: SizedIterable<ExportLine> = SizedCollection(
            ExportLine(100L, "offline", "queued-1"),
            ExportLine(101L, "offline", "queued-2"),
            ExportLine(102L, "offline", "queued-3"),
        )
        assertThat(pendingOffline.count()).isEqualTo(3L)
        assertThat(pendingOffline.offset(1).limit(1).map(ExportLine::render)).containsExactly("101|offline|queued-2")
        assertThat(pendingOffline.mapLazy(ExportLine::action).toList()).containsExactly("queued-1", "queued-2", "queued-3")

        val noPending: SizedIterable<ExportLine> = emptySized()
        assertThat(noPending.empty()).isTrue()
        assertThat(noPending.count()).isEqualTo(0L)
        assertThat(noPending.toList()).isEmpty()

        transaction(database) {
            SchemaUtils.drop(AuditEvents, Severities)
        }
    }
}
