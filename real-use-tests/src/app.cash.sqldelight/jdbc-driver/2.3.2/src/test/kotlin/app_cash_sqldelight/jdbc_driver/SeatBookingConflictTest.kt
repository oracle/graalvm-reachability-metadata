/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import java.sql.SQLIntegrityConstraintViolationException
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC constraint-error propagation for conflicting seat-booking requests.
 */
@Timeout(60)
class SeatBookingConflictTest {
    @Test
    fun rejectsAnOccupiedSeatAndAcceptsNewBookingsAfterCancellation() {
        val dataSource: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:seat_booking_conflicts")
        }
        // Retain the database while individual booking requests borrow connections.
        dataSource.connection.use {
            dataSource.asJdbcDriver().use { driver: JdbcDriver ->
                driver.execute(
                    null,
                    """
                    CREATE TABLE bookings (
                        event VARCHAR(80) NOT NULL,
                        seat VARCHAR(10) NOT NULL,
                        customer VARCHAR(80) NOT NULL,
                        CONSTRAINT occupied_seat UNIQUE (event, seat)
                    )
                    """.trimIndent(),
                    0
                )
                val desk: BookingDesk = BookingDesk(driver)
                val alice: Booking = Booking("matinee", "A01", "alice")
                val bob: Booking = Booking("matinee", "A02", "bob")
                val evening: Booking = Booking("evening", "A01", "carol")

                assertEquals(BookingResult.CONFIRMED, desk.reserve(alice))
                assertEquals(BookingResult.SEAT_TAKEN, desk.reserve(alice.copy(customer = "bob")))
                assertEquals(listOf(alice), desk.list("matinee"))

                // A rejected statement must not prevent a subsequent valid request.
                assertEquals(BookingResult.CONFIRMED, desk.reserve(bob))
                assertEquals(BookingResult.CONFIRMED, desk.reserve(evening))
                assertEquals(listOf(alice, bob), desk.list("matinee"))
                assertEquals(listOf(evening), desk.list("evening"))

                // Another customer cannot cancel Alice's reservation.
                assertEquals(0L, desk.cancel(alice.copy(customer = "bob")))
                assertEquals(BookingResult.SEAT_TAKEN, desk.reserve(alice.copy(customer = "dana")))
                assertEquals(listOf(alice, bob), desk.list("matinee"))

                assertEquals(1L, desk.cancel(alice))
                val replacement: Booking = alice.copy(customer = "dana")
                assertEquals(BookingResult.CONFIRMED, desk.reserve(replacement))
                assertEquals(listOf(replacement, bob), desk.list("matinee"))
                assertEquals(listOf(evening), desk.list("evening"))
            }
        }
    }

    private class BookingDesk(private val driver: JdbcDriver) {
        fun reserve(booking: Booking): BookingResult {
            try {
                val changed: Long = driver.execute(
                    null,
                    "INSERT INTO bookings (event, seat, customer) VALUES (?, ?, ?)",
                    3
                ) {
                    bindString(0, booking.event)
                    bindString(1, booking.seat)
                    bindString(2, booking.customer)
                }.value
                check(changed == 1L)
                return BookingResult.CONFIRMED
            } catch (failure: SQLIntegrityConstraintViolationException) {
                // Only a uniqueness violation represents an occupied seat.
                if (failure.sqlState != "23505") {
                    throw failure
                }
                return BookingResult.SEAT_TAKEN
            }
        }

        fun cancel(booking: Booking): Long = driver.execute(
            null,
            "DELETE FROM bookings WHERE event = ? AND seat = ? AND customer = ?",
            3
        ) {
            bindString(0, booking.event)
            bindString(1, booking.seat)
            bindString(2, booking.customer)
        }.value

        fun list(event: String): List<Booking> = driver.executeQuery(
            null,
            "SELECT event, seat, customer FROM bookings WHERE event = ? ORDER BY seat",
            mapper = { cursor ->
                val bookings: MutableList<Booking> = mutableListOf()
                while (cursor.next().value) {
                    bookings.add(
                        Booking(
                            requireNotNull(cursor.getString(0)),
                            requireNotNull(cursor.getString(1)),
                            requireNotNull(cursor.getString(2))
                        )
                    )
                }
                QueryResult.Value(bookings.toList())
            },
            parameters = 1,
            binders = { bindString(0, event) }
        ).value
    }

    private data class Booking(val event: String, val seat: String, val customer: String)

    private enum class BookingResult { CONFIRMED, SEAT_TAKEN }
}
