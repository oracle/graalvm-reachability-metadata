/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.DecimalColumnType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.bitwiseAnd
import org.jetbrains.exposed.v1.core.bitwiseOr
import org.jetbrains.exposed.v1.core.bitwiseXor
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.decimalLiteral
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.functions.math.AbsFunction
import org.jetbrains.exposed.v1.core.functions.math.CeilingFunction
import org.jetbrains.exposed.v1.core.functions.math.FloorFunction
import org.jetbrains.exposed.v1.core.functions.math.PowerFunction
import org.jetbrains.exposed.v1.core.functions.math.RoundFunction
import org.jetbrains.exposed.v1.core.functions.math.SignFunction
import org.jetbrains.exposed.v1.core.functions.math.SqrtFunction
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.hasFlag
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.rem
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test

/**
 * Numeric arithmetic, math functions and bitwise flag operations: a utility biller derives
 * consumption, rounded energy charges, late-fee factors and billed hours in SQL with `ROUND`,
 * `ABS`, `SIGN`, `CEILING`, `FLOOR`, `POWER`, `SQRT`, `%` and `CAST`, and manages account
 * preferences stored as bit flags with bitwise AND, OR and XOR.
 */
class UtilityBillingMathExpressionsTest {

    object Accounts : Table("utility_accounts") {
        val id = integer("id")
        val holder = varchar("holder", 64)
        val flags = integer("flags")

        override val primaryKey = PrimaryKey(id)
    }

    object Readings : Table("meter_readings") {
        val accountId = integer("account_id").references(Accounts.id)
        val previousKwh = integer("previous_kwh")
        val currentKwh = integer("current_kwh")
        val ratePerKwh = decimal("rate_per_kwh", 8, 4)
        val adjustment = decimal("adjustment", 10, 2)
        val serviceHours = double("service_hours")
        val monthsOverdue = integer("months_overdue")
        val peakKw = integer("peak_kw")
    }

    data class Reading(
        val accountId: Int,
        val previousKwh: Int,
        val currentKwh: Int,
        val ratePerKwh: String,
        val adjustment: String,
        val serviceHours: Double,
        val monthsOverdue: Int,
        val peakKw: Int,
    )

    data class Statement(
        val accountId: Int,
        val consumption: Int,
        val energyCharge: BigDecimal,
        val adjustmentSize: BigDecimal,
        val adjustmentSign: Int,
        val billedHours: Long,
        val fullHours: Long,
        val billingCycle: Int,
    )

    @Test
    fun computesBillsWithMathFunctionsAndManagesFlagsBitwise() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:utility_billing;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(database) {
            SchemaUtils.create(Accounts, Readings)
            Accounts.batchInsert(
                listOf(
                    Triple(1, "Ada", PAPERLESS or AUTOPAY),
                    Triple(2, "Brook", PAPERLESS),
                    Triple(3, "Cyd", AUTOPAY),
                    Triple(4, "Dee", 0),
                ),
            ) { (accountId: Int, name: String, accountFlags: Int) ->
                this[Accounts.id] = accountId
                this[Accounts.holder] = name
                this[Accounts.flags] = accountFlags
            }
            Readings.batchInsert(
                listOf(
                    Reading(1, 1200, 1530, "0.1525", "-12.50", 3.25, 0, 9),
                    Reading(2, 800, 1010, "0.1800", "4.00", 1.0, 2, 16),
                    Reading(3, 4000, 4875, "0.1250", "0.00", 0.4, 1, 25),
                    Reading(4, 300, 333, "0.2100", "-3.75", 6.75, 3, 4),
                ),
            ) { reading: Reading ->
                this[Readings.accountId] = reading.accountId
                this[Readings.previousKwh] = reading.previousKwh
                this[Readings.currentKwh] = reading.currentKwh
                this[Readings.ratePerKwh] = BigDecimal(reading.ratePerKwh)
                this[Readings.adjustment] = BigDecimal(reading.adjustment)
                this[Readings.serviceHours] = reading.serviceHours
                this[Readings.monthsOverdue] = reading.monthsOverdue
                this[Readings.peakKw] = reading.peakKw
            }
        }

        val consumption = Readings.currentKwh - Readings.previousKwh
        val energyCharge = RoundFunction(
            consumption.castTo(DecimalColumnType(12, 4)) * Readings.ratePerKwh,
            2,
        )
        val adjustmentSize = AbsFunction(Readings.adjustment)
        val adjustmentSign = SignFunction(Readings.adjustment)
        val billedHours = CeilingFunction(Readings.serviceHours)
        val fullHours = FloorFunction(Readings.serviceHours)
        val billingCycle = Readings.accountId rem 3

        val statements: List<Statement> = transaction(database) {
            Readings
                .select(
                    Readings.accountId,
                    consumption,
                    energyCharge,
                    adjustmentSize,
                    adjustmentSign,
                    billedHours,
                    fullHours,
                    billingCycle,
                )
                .orderBy(energyCharge, SortOrder.DESC)
                .map { row ->
                    Statement(
                        accountId = row[Readings.accountId],
                        consumption = row[consumption],
                        energyCharge = checkNotNull(row[energyCharge]),
                        adjustmentSize = row[adjustmentSize],
                        adjustmentSign = checkNotNull(row[adjustmentSign]),
                        billedHours = checkNotNull(row[billedHours]),
                        fullHours = checkNotNull(row[fullHours]),
                        billingCycle = row[billingCycle],
                    )
                }
        }
        assertThat(statements.map { it.accountId }).containsExactly(3, 1, 2, 4)
        val byAccount: Map<Int, Statement> = statements.associateBy { it.accountId }
        // 330 kWh at 0.1525 is 50.325, which rounds half up to 50.33.
        assertThat(byAccount.getValue(1).consumption).isEqualTo(330)
        assertThat(byAccount.getValue(1).energyCharge).isEqualByComparingTo("50.33")
        assertThat(byAccount.getValue(2).energyCharge).isEqualByComparingTo("37.80")
        assertThat(byAccount.getValue(3).energyCharge).isEqualByComparingTo("109.38")
        assertThat(byAccount.getValue(4).energyCharge).isEqualByComparingTo("6.93")
        assertThat(byAccount.getValue(1).adjustmentSize).isEqualByComparingTo("12.50")
        assertThat(byAccount.getValue(4).adjustmentSize).isEqualByComparingTo("3.75")
        assertThat(statements.associate { it.accountId to it.adjustmentSign })
            .containsExactlyInAnyOrderEntriesOf(mapOf(1 to -1, 2 to 1, 3 to 0, 4 to -1))
        assertThat(statements.associate { it.accountId to (it.fullHours to it.billedHours) })
            .containsExactlyInAnyOrderEntriesOf(
                mapOf(1 to (3L to 4L), 2 to (1L to 1L), 3 to (0L to 1L), 4 to (6L to 7L)),
            )
        assertThat(statements.associate { it.accountId to it.billingCycle })
            .containsExactlyInAnyOrderEntriesOf(mapOf(1 to 1, 2 to 2, 3 to 0, 4 to 1))

        // Late fees compound monthly: the balance grows by POWER(1.05, months overdue).
        val lateFactor = PowerFunction(
            decimalLiteral(BigDecimal("1.05")),
            Readings.monthsOverdue,
            precision = 10,
            scale = 6,
        )
        // Demand charges scale with the square root of the metered peak load.
        val demandRoot = SqrtFunction(Readings.peakKw)
        val surcharges: Map<Int, Pair<BigDecimal, BigDecimal>> = transaction(database) {
            Readings
                .select(Readings.accountId, lateFactor, demandRoot)
                .where { AbsFunction(Readings.adjustment) greater BigDecimal("1.00") }
                .associate { row ->
                    row[Readings.accountId] to
                        (checkNotNull(row[lateFactor]) to checkNotNull(row[demandRoot]))
                }
        }
        assertThat(surcharges.keys).containsExactlyInAnyOrder(1, 2, 4)
        assertThat(surcharges.getValue(1).first).isEqualByComparingTo("1")
        assertThat(surcharges.getValue(2).first).isEqualByComparingTo("1.1025")
        assertThat(surcharges.getValue(4).first).isEqualByComparingTo("1.157625")
        assertThat(surcharges.getValue(1).second).isEqualByComparingTo("3")
        assertThat(surcharges.getValue(2).second).isEqualByComparingTo("4")
        assertThat(surcharges.getValue(4).second).isEqualByComparingTo("2")

        transaction(database) {
            val overdueAccounts: List<Int> = Readings
                .select(Readings.accountId)
                .where { Readings.monthsOverdue greater 0 }
                .map { it[Readings.accountId] }
            // Flag every overdue account and flip the paperless preference for account 2.
            Accounts.update({ Accounts.id inList overdueAccounts }) {
                it[flags] = Accounts.flags bitwiseOr OVERDUE_NOTICE
            }
            Accounts.update({ Accounts.id eq 2 }) {
                it[flags] = Accounts.flags bitwiseXor PAPERLESS
            }
        }

        transaction(database) {
            val flagsByHolder: Map<String, Int> = Accounts.selectAll()
                .associate { it[Accounts.holder] to it[Accounts.flags] }
            assertThat(flagsByHolder).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                    "Ada" to (PAPERLESS or AUTOPAY),
                    "Brook" to OVERDUE_NOTICE,
                    "Cyd" to (AUTOPAY or OVERDUE_NOTICE),
                    "Dee" to OVERDUE_NOTICE,
                ),
            )

            val autopayHolders: List<String> = Accounts.select(Accounts.holder)
                .where { Accounts.flags hasFlag AUTOPAY }
                .orderBy(Accounts.holder)
                .map { it[Accounts.holder] }
            assertThat(autopayHolders).containsExactly("Ada", "Cyd")

            val noticeMask = Accounts.flags bitwiseAnd (OVERDUE_NOTICE or AUTOPAY)
            val masks: Map<String, Int> = Accounts.select(Accounts.holder, noticeMask)
                .associate { it[Accounts.holder] to it[noticeMask] }
            assertThat(masks).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                    "Ada" to AUTOPAY,
                    "Brook" to OVERDUE_NOTICE,
                    "Cyd" to (AUTOPAY or OVERDUE_NOTICE),
                    "Dee" to OVERDUE_NOTICE,
                ),
            )
        }
    }

    companion object {
        const val PAPERLESS: Int = 1
        const val AUTOPAY: Int = 2
        const val OVERDUE_NOTICE: Int = 4
    }
}
