/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.fx.coroutines.parZip
import arrow.resilience.Saga
import arrow.resilience.saga
import arrow.resilience.transact
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises distributed transactions built from Arrow `Saga` steps that `parZip` books
 * concurrently, with every completed step compensated in reverse order when a later step fails.
 *
 * A trip booking service holds a fare, then books a flight seat and a hotel room in parallel
 * branches of one `parZip`, and finally charges the traveller. Each step writes to an in-memory
 * H2 database and registers an undo action, so a failure anywhere leaves the inventory as it was.
 */
public class TripBookingSagaParZipTest {

    /** A confirmed trip: the seat and room booked and the payment that settled them. */
    public data class Itinerary(val seat: String, val room: String, val paymentId: Long)

    /** Raised when an inventory has nothing left to book. */
    public class SoldOutException(message: String) : RuntimeException(message)

    /** Raised when the payment provider refuses a charge. */
    public class PaymentDeclinedException(message: String) : RuntimeException(message)

    /** Inventory and payments for the booking service, stored in H2 tables. */
    public class TravelStore(private val dataSource: HikariDataSource) {

        public fun createSchema() {
            execute("CREATE TABLE seat(id VARCHAR(8) PRIMARY KEY, traveller VARCHAR(32))")
            execute("CREATE TABLE room(id VARCHAR(8) PRIMARY KEY, traveller VARCHAR(32))")
            execute("CREATE TABLE fare_hold(traveller VARCHAR(32) PRIMARY KEY, amount BIGINT)")
            execute("CREATE TABLE payment(id BIGINT AUTO_INCREMENT PRIMARY KEY, traveller VARCHAR(32), amount BIGINT)")
            execute("INSERT INTO seat(id) VALUES ('12A'), ('12B')")
            execute("INSERT INTO room(id) VALUES ('301')")
        }

        public fun holdFare(traveller: String, amount: Long): Unit =
            update("INSERT INTO fare_hold(traveller, amount) VALUES (?, ?)", traveller, amount)

        public fun releaseFare(traveller: String): Unit =
            update("DELETE FROM fare_hold WHERE traveller = ?", traveller)

        public fun bookSeat(traveller: String): String = claim("seat", traveller)

        public fun releaseSeat(seat: String): Unit = update("UPDATE seat SET traveller = NULL WHERE id = ?", seat)

        public fun bookRoom(traveller: String): String = claim("room", traveller)

        public fun releaseRoom(room: String): Unit = update("UPDATE room SET traveller = NULL WHERE id = ?", room)

        public fun charge(traveller: String, amount: Long): Long =
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    "INSERT INTO payment(traveller, amount) VALUES (?, ?)",
                    arrayOf("ID"),
                ).use { statement ->
                    statement.setString(1, traveller)
                    statement.setLong(2, amount)
                    statement.executeUpdate()
                    statement.generatedKeys.use { keys ->
                        check(keys.next()) { "no payment id generated" }
                        keys.getLong(1)
                    }
                }
            }

        public fun refund(paymentId: Long): Unit = update("DELETE FROM payment WHERE id = ?", paymentId)

        public fun count(sql: String): Int =
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(sql).use { rows ->
                        check(rows.next())
                        rows.getInt(1)
                    }
                }
            }

        private fun claim(table: String, traveller: String): String =
            dataSource.connection.use { connection ->
                val free: String = connection.prepareStatement(
                    "SELECT MIN(id) FROM $table WHERE traveller IS NULL",
                ).use { statement ->
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getString(1) ?: throw SoldOutException("no $table left for $traveller")
                    }
                }
                connection.prepareStatement("UPDATE $table SET traveller = ? WHERE id = ?").use { statement ->
                    statement.setString(1, traveller)
                    statement.setString(2, free)
                    check(statement.executeUpdate() == 1)
                }
                free
            }

        private fun execute(sql: String) {
            dataSource.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
        }

        private fun update(sql: String, vararg parameters: Any) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    parameters.forEachIndexed { index: Int, value: Any -> statement.setObject(index + 1, value) }
                    statement.executeUpdate()
                }
            }
        }
    }

    /** Builds trip sagas over a store, recording each action and compensation as it happens. */
    public class TripBooking(private val store: TravelStore, private val maxCharge: Long) {

        public val journal: MutableList<String> = Collections.synchronizedList(mutableListOf())

        /** Completed when the flight seat is booked, so a dependent branch can fail after it. */
        public val seatBooked: CompletableDeferred<Unit> = CompletableDeferred()

        private fun holdFare(traveller: String, amount: Long): Saga<Unit> = saga {
            saga({
                store.holdFare(traveller, amount)
                journal.add("hold fare")
            }) {
                store.releaseFare(traveller)
                journal.add("release fare")
            }
        }

        public fun trip(traveller: String, amount: Long, roomAfterSeat: Boolean = false): Saga<Itinerary> = saga {
            holdFare(traveller, amount).bind()
            val (seat: String, room: String) = parZip(
                {
                    saga({
                        store.bookSeat(traveller).also { journal.add("book seat $it") }
                    }) { seat: String ->
                        store.releaseSeat(seat)
                        journal.add("release seat $seat")
                    }.also { seatBooked.complete(Unit) }
                },
                {
                    if (roomAfterSeat) {
                        seatBooked.await()
                    }
                    saga({
                        store.bookRoom(traveller).also { journal.add("book room $it") }
                    }) { room: String ->
                        store.releaseRoom(room)
                        journal.add("release room $room")
                    }
                },
            ) { seat: String, room: String -> seat to room }
            val paymentId: Long = saga({
                if (amount > maxCharge) {
                    throw PaymentDeclinedException("charge of $amount exceeds limit $maxCharge")
                }
                store.charge(traveller, amount).also { journal.add("charge $amount") }
            }) { id: Long ->
                store.refund(id)
                journal.add("refund $id")
            }
            Itinerary(seat, room, paymentId)
        }
    }

    private lateinit var dataSource: HikariDataSource
    private lateinit var store: TravelStore

    @BeforeEach
    public fun openDatabase() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:trip-saga-${System.nanoTime()};DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            maximumPoolSize = 4
            poolName = "trip-saga"
        }
        dataSource = HikariDataSource(config)
        store = TravelStore(dataSource)
        store.createSchema()
    }

    @AfterEach
    public fun closeDatabase() {
        dataSource.close()
    }

    @Test
    public fun completedTripKeepsEveryStepAndRunsNoCompensation(): Unit = runBlocking {
        val booking = TripBooking(store, maxCharge = 1_000)

        val itinerary: Itinerary = withTimeout(10_000) { booking.trip("ada", 640).transact() }

        assertEquals("12A", itinerary.seat)
        assertEquals("301", itinerary.room)
        assertEquals(1, store.count("SELECT COUNT(*) FROM payment WHERE id = ${itinerary.paymentId}"))
        assertEquals(1, store.count("SELECT COUNT(*) FROM fare_hold WHERE traveller = 'ada'"))
        assertEquals(1, store.count("SELECT COUNT(*) FROM seat WHERE traveller = 'ada'"))
        assertEquals(1, store.count("SELECT COUNT(*) FROM room WHERE traveller = 'ada'"))
        assertEquals("hold fare", booking.journal.first())
        assertEquals("charge 640", booking.journal.last())
        assertTrue(booking.journal.none { it.startsWith("release") || it.startsWith("refund") }) {
            "unexpected compensation in ${booking.journal}"
        }
    }

    @Test
    public fun declinedPaymentCompensatesParallelBookingsBeforeTheFareHold(): Unit = runBlocking {
        val booking = TripBooking(store, maxCharge = 500)

        val failure: PaymentDeclinedException = assertThrows(PaymentDeclinedException::class.java) {
            runBlocking { withTimeout(10_000) { booking.trip("grace", 640).transact() } }
        }

        assertEquals("charge of 640 exceeds limit 500", failure.message)
        assertEquals(0, store.count("SELECT COUNT(*) FROM payment"))
        assertEquals(0, store.count("SELECT COUNT(*) FROM fare_hold"))
        assertEquals(0, store.count("SELECT COUNT(*) FROM seat WHERE traveller IS NOT NULL"))
        assertEquals(0, store.count("SELECT COUNT(*) FROM room WHERE traveller IS NOT NULL"))
        val journal: List<String> = booking.journal.toList()
        assertEquals(6, journal.size, "journal was $journal")
        assertEquals("hold fare", journal[0])
        assertEquals(setOf("book seat 12A", "book room 301"), journal.subList(1, 3).toSet())
        assertEquals(setOf("release seat 12A", "release room 301"), journal.subList(3, 5).toSet())
        assertEquals("release fare", journal[5])
    }

    @Test
    public fun soldOutBranchFailsTheTripAndUndoesTheSiblingThatAlreadyBooked(): Unit = runBlocking {
        TripBooking(store, maxCharge = 1_000).trip("alan", 300).transact()
        val booking = TripBooking(store, maxCharge = 1_000)

        val failure: SoldOutException = assertThrows(SoldOutException::class.java) {
            runBlocking { withTimeout(10_000) { booking.trip("edsger", 300, roomAfterSeat = true).transact() } }
        }

        assertEquals("no room left for edsger", failure.message)
        assertEquals(
            listOf("hold fare", "book seat 12B", "release seat 12B", "release fare"),
            booking.journal.toList(),
        )
        assertEquals(1, store.count("SELECT COUNT(*) FROM payment"))
        assertEquals(1, store.count("SELECT COUNT(*) FROM fare_hold WHERE traveller = 'alan'"))
        assertEquals(0, store.count("SELECT COUNT(*) FROM fare_hold WHERE traveller = 'edsger'"))
        assertEquals(0, store.count("SELECT COUNT(*) FROM seat WHERE traveller = 'edsger'"))
        assertEquals(1, store.count("SELECT COUNT(*) FROM seat WHERE traveller IS NULL"))
    }
}
