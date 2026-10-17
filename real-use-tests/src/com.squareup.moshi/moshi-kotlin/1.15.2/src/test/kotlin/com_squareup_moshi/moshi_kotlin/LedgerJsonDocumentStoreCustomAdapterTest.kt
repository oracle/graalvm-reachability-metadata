/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import com.squareup.moshi.adapters.EnumJsonAdapter
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.Date as SqlDate
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDate
import java.util.Date

/**
 * Exercises custom adapter composition around Kotlin models: `@ToJson`/`@FromJson` adapter
 * objects, a `@JsonQualifier` on Kotlin properties, an intermediate wire-type conversion that
 * validates on decode, and `EnumJsonAdapter`/`Rfc3339DateJsonAdapter` registered per type.
 *
 * A bookkeeping service keeps journal entries as JSON documents in an H2 table, reloads them,
 * exports a period as a JSON array, and imports entries from a partner feed.
 */
@OptIn(ExperimentalStdlibApi::class)
public class LedgerJsonDocumentStoreCustomAdapterTest {

    /** Marks a `Long` amount in cents that travels as a decimal string such as `"12.50"`. */
    @Retention(AnnotationRetention.RUNTIME)
    @JsonQualifier
    public annotation class DecimalAmount

    /** Bookkeeping category; partners may send categories this service does not know. */
    public enum class Category {
        @Json(name = "rent")
        RENT,

        @Json(name = "payroll")
        PAYROLL,

        @Json(name = "sales")
        SALES,

        @Json(name = "other")
        OTHER,
    }

    /** One debit or credit line of a journal entry. */
    public data class Line(
        val account: String,
        @DecimalAmount val debit: Long = 0L,
        @DecimalAmount val credit: Long = 0L,
    )

    /** The journal entry as it travels and is stored; bound reflectively. */
    public data class JournalEntryJson(
        val id: String,
        @Json(name = "booked_on") val bookedOn: LocalDate,
        @Json(name = "recorded_at") val recordedAt: Date,
        val category: Category,
        val memo: String? = null,
        val lines: List<Line>,
    )

    /** The journal entry the application works with; always balanced. */
    public data class JournalEntry(
        val id: String,
        val bookedOn: LocalDate,
        val recordedAt: Date,
        val category: Category,
        val memo: String?,
        val lines: List<Line>,
    ) {
        val total: Long
            get() = lines.sumOf { it.debit }
    }

    /** Adapter methods the service registers with its `Moshi` instance. */
    public object LedgerJsonAdapters {
        private val decimal: Regex = Regex("""(\d+)\.(\d{2})""")

        @ToJson
        fun amountToJson(@DecimalAmount cents: Long): String =
            "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

        @FromJson
        @DecimalAmount
        fun amountFromJson(text: String): Long {
            val match: MatchResult = decimal.matchEntire(text)
                ?: throw JsonDataException("Invalid amount '$text'")
            return match.groupValues[1].toLong() * 100 + match.groupValues[2].toLong()
        }

        @ToJson
        fun dateToJson(date: LocalDate): String = date.toString()

        @FromJson
        fun dateFromJson(text: String): LocalDate = LocalDate.parse(text)

        @ToJson
        fun entryToJson(entry: JournalEntry): JournalEntryJson = JournalEntryJson(
            entry.id, entry.bookedOn, entry.recordedAt, entry.category, entry.memo, entry.lines,
        )

        @FromJson
        fun entryFromJson(json: JournalEntryJson): JournalEntry {
            val debits: Long = json.lines.sumOf { it.debit }
            val credits: Long = json.lines.sumOf { it.credit }
            if (debits != credits) {
                throw JsonDataException("Entry ${json.id} is unbalanced: $debits != $credits")
            }
            return JournalEntry(
                json.id, json.bookedOn, json.recordedAt, json.category, json.memo, json.lines,
            )
        }
    }

    /** Stores journal entries as JSON documents keyed by id and booking date. */
    public class LedgerStore(
        private val connection: Connection,
        private val entryAdapter: JsonAdapter<JournalEntry>,
    ) {
        init {
            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TABLE journal (
                      id VARCHAR(64) PRIMARY KEY,
                      booked_on DATE NOT NULL,
                      document CLOB NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun save(entry: JournalEntry) {
            connection.prepareStatement("INSERT INTO journal VALUES (?, ?, ?)").use {
                it.setString(1, entry.id)
                it.setDate(2, SqlDate.valueOf(entry.bookedOn))
                it.setString(3, entryAdapter.toJson(entry))
                it.executeUpdate()
            }
        }

        fun rawDocument(id: String): String? =
            connection.prepareStatement("SELECT document FROM journal WHERE id = ?").use {
                it.setString(1, id)
                it.executeQuery().use { rows: ResultSet ->
                    if (rows.next()) rows.getString(1) else null
                }
            }

        fun load(id: String): JournalEntry? = rawDocument(id)?.let { entryAdapter.fromJson(it) }

        fun bookedBetween(from: LocalDate, to: LocalDate): List<JournalEntry> {
            val sql: String = """
                SELECT document FROM journal
                WHERE booked_on BETWEEN ? AND ?
                ORDER BY booked_on, id
            """.trimIndent()
            return connection.prepareStatement(sql).use { statement: PreparedStatement ->
                statement.setDate(1, SqlDate.valueOf(from))
                statement.setDate(2, SqlDate.valueOf(to))
                statement.executeQuery().use { rows: ResultSet ->
                    buildList {
                        while (rows.next()) {
                            add(entryAdapter.fromJson(rows.getString(1))!!)
                        }
                    }
                }
            }
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(LedgerJsonAdapters)
        .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
        .add(
            Category::class.java,
            EnumJsonAdapter.create(Category::class.java).withUnknownFallback(Category.OTHER),
        )
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val entryAdapter: JsonAdapter<JournalEntry> = moshi.adapter<JournalEntry>()

    private lateinit var connection: Connection

    private lateinit var store: LedgerStore

    @BeforeEach
    fun openLedger() {
        connection = DriverManager.getConnection("jdbc:h2:mem:ledger", "sa", "")
        store = LedgerStore(connection, entryAdapter)
    }

    @AfterEach
    fun closeLedger() {
        connection.close()
    }

    private fun rentEntry(): JournalEntry = JournalEntry(
        id = "je-2026-0001",
        bookedOn = LocalDate.of(2026, 3, 1),
        recordedAt = Date(1_772_355_600_000L),
        category = Category.RENT,
        memo = "March office rent",
        lines = listOf(
            Line("6100-rent", debit = 185_000L),
            Line("1000-bank", credit = 185_000L),
        ),
    )

    @Test
    fun savedEntryIsStoredAsQualifiedJsonAndReloadsEqual() {
        val entry: JournalEntry = rentEntry()

        store.save(entry)

        assertEquals(
            """{"id":"je-2026-0001","booked_on":"2026-03-01",""" +
                """"recorded_at":"2026-03-01T09:00:00.000Z","category":"rent",""" +
                """"memo":"March office rent","lines":[""" +
                """{"account":"6100-rent","debit":"1850.00","credit":"0.00"},""" +
                """{"account":"1000-bank","debit":"0.00","credit":"1850.00"}]}""",
            store.rawDocument("je-2026-0001"),
        )
        val reloaded: JournalEntry = store.load("je-2026-0001")!!
        assertEquals(entry, reloaded)
        assertEquals(185_000L, reloaded.total)
        assertNull(store.load("je-missing"))
    }

    @Test
    fun periodExportSerializesStoredEntriesAsJsonArray() {
        val rent: JournalEntry = rentEntry()
        val payroll: JournalEntry = rent.copy(
            id = "je-2026-0002",
            bookedOn = LocalDate.of(2026, 3, 25),
            category = Category.PAYROLL,
            memo = null,
            lines = listOf(
                Line("6200-wages", debit = 420_050L),
                Line("2100-tax-payable", credit = 80_050L),
                Line("1000-bank", credit = 340_000L),
            ),
        )
        val april: JournalEntry = rent.copy(id = "je-2026-0003", bookedOn = LocalDate.of(2026, 4, 1))
        listOf(april, payroll, rent).forEach(store::save)

        val march: List<JournalEntry> =
            store.bookedBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
        val exportAdapter: JsonAdapter<List<JournalEntry>> = moshi.adapter<List<JournalEntry>>()
        val export: String = exportAdapter.toJson(march)

        assertEquals(listOf(rent, payroll), march)
        assertTrue(export.startsWith("""[{"id":"je-2026-0001""""), export)
        assertTrue(
            export.contains(
                """"category":"payroll","lines":[{"account":"6200-wages","debit":"4200.50"""",
            ),
            export,
        )
        assertEquals(march, exportAdapter.fromJson(export))
        assertEquals(listOf(185_000L, 420_050L), march.map { it.total })
    }

    @Test
    fun unknownPartnerCategoryFallsBackToOther() {
        val feed: String = """
            {"id":"je-partner-17","booked_on":"2026-05-10",
             "recorded_at":"2026-05-10T14:30:00+02:00","category":"consulting",
             "lines":[{"account":"6400-fees","debit":"99.90"},
                      {"account":"2000-payable","credit":"99.90"}]}
        """.trimIndent()

        val entry: JournalEntry = entryAdapter.fromJson(feed)!!

        assertEquals(Category.OTHER, entry.category)
        assertNull(entry.memo)
        assertEquals(Date(1_778_416_200_000L), entry.recordedAt)
        assertEquals(listOf(Line("6400-fees", 9_990L, 0L), Line("2000-payable", 0L, 9_990L)), entry.lines)
        store.save(entry)
        assertTrue(store.rawDocument("je-partner-17")!!.contains(""""category":"other""""))
    }

    @Test
    fun unbalancedEntryIsRejectedByTheConversionAdapter() {
        val feed: String = """
            {"id":"je-partner-18","booked_on":"2026-05-11",
             "recorded_at":"2026-05-11T08:00:00Z","category":"sales",
             "lines":[{"account":"1100-receivable","debit":"500.00"},
                      {"account":"4000-revenue","credit":"450.00"}]}
        """.trimIndent()

        val failure: JsonDataException =
            assertThrows(JsonDataException::class.java) { entryAdapter.fromJson(feed) }

        assertTrue(
            failure.message!!.contains("Entry je-partner-18 is unbalanced: 50000 != 45000"),
            failure.message,
        )
        assertTrue(store.bookedBetween(LocalDate.MIN, LocalDate.MAX).isEmpty())
    }

    @Test
    fun malformedAmountIsRejectedByTheQualifiedAdapter() {
        val feed: String = """
            {"id":"je-partner-19","booked_on":"2026-05-12",
             "recorded_at":"2026-05-12T08:00:00Z","category":"sales",
             "lines":[{"account":"1100-receivable","debit":"12.5"}]}
        """.trimIndent()

        val failure: JsonDataException =
            assertThrows(JsonDataException::class.java) { entryAdapter.fromJson(feed) }

        assertTrue(failure.message!!.contains("Invalid amount '12.5'"), failure.message)
        assertTrue(failure.message!!.contains("$.lines[0].debit"), failure.message)
    }

    @Test
    fun strictImportRejectsUnknownFieldsThatLenientImportSkips() {
        val feed: String = """
            {"id":"je-partner-20","booked_on":"2026-05-13",
             "recorded_at":"2026-05-13T08:00:00Z","category":"sales","approved_by":"ops",
             "lines":[{"account":"1000-bank","debit":"10.00"},
                      {"account":"4000-revenue","credit":"10.00"}]}
        """.trimIndent()

        val strict: JsonDataException = assertThrows(JsonDataException::class.java) {
            entryAdapter.failOnUnknown().fromJson(feed)
        }
        val imported: JournalEntry = entryAdapter.fromJson(feed)!!

        assertTrue(strict.message!!.contains("approved_by"), strict.message)
        assertEquals(Category.SALES, imported.category)
        assertEquals(1_000L, imported.total)
    }
}
