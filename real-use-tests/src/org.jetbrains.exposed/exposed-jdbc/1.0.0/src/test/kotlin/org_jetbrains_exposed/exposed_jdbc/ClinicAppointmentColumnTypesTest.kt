/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.ColumnTransformer
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.javatime.duration
import org.jetbrains.exposed.v1.javatime.durationParam
import org.jetbrains.exposed.v1.javatime.hour
import org.jetbrains.exposed.v1.javatime.month
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.year
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test

/**
 * Column type mapping between Kotlin values and SQL types: a clinic booking service stores
 * appointments in `java.time`, enum, `Uuid`, decimal, binary, blob and transformed columns with
 * client-side and database-side defaults and a check constraint, then queries them by date parts.
 */
@OptIn(ExperimentalUuidApi::class)
class ClinicAppointmentColumnTypesTest {

    enum class Status { BOOKED, CONFIRMED, CANCELLED }

    enum class Priority { ROUTINE, SOON, URGENT }

    @JvmInline
    value class Money(val cents: Long)

    object MoneyTransformer : ColumnTransformer<Long, Money> {
        override fun wrap(value: Long): Money = Money(value)

        override fun unwrap(value: Money): Long = value.cents
    }

    object Appointments : Table("appointments") {
        val id = uuid("id").clientDefault { Uuid.random() }
        val patient = varchar("patient", 64)
        val day = date("day")
        val startsAt = time("starts_at")
        val scheduledFor = datetime("scheduled_for")
        val length = duration("length")
        val status = enumerationByName<Status>("status", 16).default(Status.BOOKED)
        val priority = enumeration<Priority>("priority")
        val fee = decimal("fee", 8, 2).check("fee_not_negative") { it greaterEq BigDecimal.ZERO }
        val deposit = long("deposit_cents").transform(MoneyTransformer)
        val insuranceCode = varchar("insurance_code", 16).nullable()
        val signature = binary("signature", 32).nullable()
        val referral = blob("referral").nullable()
        val bookedAt = timestamp("booked_at").defaultExpression(CurrentTimestamp)
        val confirmedAt = timestampWithTimeZone("confirmed_at").nullable()
        val active = bool("active").default(true)

        override val primaryKey = PrimaryKey(id)
    }

    data class Appointment(
        val patient: String,
        val scheduledFor: LocalDateTime,
        val length: Duration,
        val priority: Priority,
        val fee: BigDecimal,
        val deposit: Money,
    )

    @Test
    fun mapsAppointmentColumnsBetweenKotlinValuesAndSqlTypes() {
        val database = Database.connect(
            url = "jdbc:h2:mem:clinic_appointments;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val knownId = Uuid.parse("5f1c2a3e-8b7d-4e6f-9a0b-1c2d3e4f5a6b")
        val signature = byteArrayOf(0x0A, 0x1B, 0x2C, 0x3D, 0x4E)
        val referralText = "Referred by Dr. Hopper for a cardiology follow-up."

        val schedule = listOf(
            Appointment(
                "Ada",
                LocalDateTime.of(2026, 3, 2, 9, 0),
                Duration.ofMinutes(30),
                Priority.ROUTINE,
                BigDecimal("45.00"),
                Money(1_000),
            ),
            Appointment(
                "Linus",
                LocalDateTime.of(2026, 3, 2, 14, 30),
                Duration.ofMinutes(60),
                Priority.URGENT,
                BigDecimal("120.50"),
                Money(2_500),
            ),
            Appointment(
                "Grace",
                LocalDateTime.of(2026, 3, 17, 11, 15),
                Duration.ofMinutes(45),
                Priority.SOON,
                BigDecimal("80.25"),
                Money(0),
            ),
            Appointment(
                "Ken",
                LocalDateTime.of(2026, 4, 6, 16, 45),
                Duration.ofMinutes(20),
                Priority.URGENT,
                BigDecimal("30.00"),
                Money(500),
            ),
        )

        transaction(database) {
            SchemaUtils.create(Appointments)

            Appointments.insert {
                it[id] = knownId
                it[patient] = "Barbara"
                it[day] = LocalDate.of(2026, 2, 27)
                it[startsAt] = LocalTime.of(8, 20)
                it[scheduledFor] = LocalDateTime.of(2026, 2, 27, 8, 20)
                it[length] = Duration.ofMinutes(90).plusSeconds(30)
                it[priority] = Priority.SOON
                it[fee] = BigDecimal("210.75")
                it[deposit] = Money(5_000)
                it[insuranceCode] = "INS-77"
                it[Appointments.signature] = signature
                it[referral] = ExposedBlob(referralText.toByteArray())
            }
            schedule.forEach { appointment ->
                Appointments.insert {
                    it[patient] = appointment.patient
                    it[day] = appointment.scheduledFor.toLocalDate()
                    it[startsAt] = appointment.scheduledFor.toLocalTime()
                    it[scheduledFor] = appointment.scheduledFor
                    it[length] = appointment.length
                    it[priority] = appointment.priority
                    it[fee] = appointment.fee
                    it[deposit] = appointment.deposit
                }
            }
        }

        transaction(database) {
            val row = Appointments.selectAll().where { Appointments.id eq knownId }.single()
            assertThat(row[Appointments.patient]).isEqualTo("Barbara")
            assertThat(row[Appointments.day]).isEqualTo(LocalDate.of(2026, 2, 27))
            assertThat(row[Appointments.startsAt]).isEqualTo(LocalTime.of(8, 20))
            assertThat(row[Appointments.scheduledFor]).isEqualTo(LocalDateTime.of(2026, 2, 27, 8, 20))
            assertThat(row[Appointments.length]).isEqualTo(Duration.ofSeconds(90 * 60 + 30))
            assertThat(row[Appointments.status]).isEqualTo(Status.BOOKED)
            assertThat(row[Appointments.priority]).isEqualTo(Priority.SOON)
            assertThat(row[Appointments.fee]).isEqualByComparingTo("210.75")
            assertThat(row[Appointments.deposit]).isEqualTo(Money(5_000))
            assertThat(row[Appointments.insuranceCode]).isEqualTo("INS-77")
            assertThat(row[Appointments.signature]).containsExactly(*signature)
            assertThat(String(row[Appointments.referral]!!.bytes)).isEqualTo(referralText)
            assertThat(row[Appointments.bookedAt]).isAfter(Instant.parse("2020-01-01T00:00:00Z"))
            assertThat(row[Appointments.confirmedAt]).isNull()
            assertThat(row[Appointments.active]).isTrue()

            val generatedIds = Appointments.select(Appointments.id)
                .where { Appointments.patient inList schedule.map { it.patient } }
                .map { it[Appointments.id] }
            assertThat(generatedIds)
                .hasSize(schedule.size)
                .doesNotHaveDuplicates()
                .doesNotContain(knownId)
        }

        val confirmation = OffsetDateTime.of(2026, 3, 1, 18, 5, 0, 0, ZoneOffset.ofHours(2))
        transaction(database) {
            val confirmed = Appointments.update({
                (Appointments.priority eq Priority.URGENT) and (Appointments.confirmedAt.isNull())
            }) {
                it[status] = Status.CONFIRMED
                it[confirmedAt] = confirmation
            }
            assertThat(confirmed).isEqualTo(2)
            Appointments.update({ Appointments.patient eq "Grace" }) {
                it[status] = Status.CANCELLED
                it[active] = false
                it[insuranceCode] = null
            }
        }

        transaction(database) {
            val urgentConfirmed = Appointments.selectAll()
                .where {
                    (Appointments.status eq Status.CONFIRMED) and
                        (Appointments.priority greaterEq Priority.SOON)
                }
                .orderBy(Appointments.scheduledFor)
                .map { it[Appointments.patient] to it[Appointments.confirmedAt]!!.toInstant() }
            assertThat(urgentConfirmed).containsExactly(
                "Linus" to confirmation.toInstant(),
                "Ken" to confirmation.toInstant(),
            )

            val marchWeek = Appointments.select(Appointments.patient)
                .where { Appointments.day.between(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 7)) }
                .orderBy(Appointments.startsAt)
                .map { it[Appointments.patient] }
            assertThat(marchWeek).containsExactly("Ada", "Linus")

            val longVisits = Appointments.select(Appointments.patient)
                .where {
                    (Appointments.length greater durationParam(Duration.ofMinutes(40))) and
                        (Appointments.active eq true)
                }
                .orderBy(Appointments.patient)
                .map { it[Appointments.patient] }
            assertThat(longVisits).containsExactly("Barbara", "Linus")

            val monthOf = Appointments.scheduledFor.month()
            val visits = Appointments.id.count()
            val perMonth = Appointments.select(monthOf, visits)
                .where { Appointments.scheduledFor.year() eq 2026 }
                .groupBy(monthOf)
                .orderBy(monthOf, SortOrder.ASC)
                .map { it[monthOf] to it[visits] }
            assertThat(perMonth).containsExactly(2 to 1L, 3 to 3L, 4 to 1L)

            val afternoon = Appointments.select(Appointments.patient)
                .where { Appointments.scheduledFor.hour() greaterEq 14 }
                .orderBy(Appointments.patient)
                .map { it[Appointments.patient] }
            assertThat(afternoon).containsExactly("Ken", "Linus")

            val grace = Appointments.selectAll().where { Appointments.patient eq "Grace" }.single()
            assertThat(grace[Appointments.status]).isEqualTo(Status.CANCELLED)
            assertThat(grace[Appointments.active]).isFalse()
            assertThat(grace[Appointments.insuranceCode]).isNull()
            assertThat(grace[Appointments.deposit]).isEqualTo(Money(0))
        }

        assertThatThrownBy {
            transaction(database) {
                Appointments.insert {
                    it[patient] = "Mallory"
                    it[day] = LocalDate.of(2026, 5, 4)
                    it[startsAt] = LocalTime.NOON
                    it[scheduledFor] = LocalDateTime.of(2026, 5, 4, 12, 0)
                    it[length] = Duration.ofMinutes(15)
                    it[priority] = Priority.ROUTINE
                    it[fee] = BigDecimal("-5.00")
                    it[deposit] = Money(0)
                }
            }
        }.isInstanceOf(ExposedSQLException::class.java)

        val total: Long = transaction(database) { Appointments.selectAll().count() }
        assertThat(total).isEqualTo(schedule.size + 1L)

        transaction(database) { SchemaUtils.drop(Appointments) }
    }
}
