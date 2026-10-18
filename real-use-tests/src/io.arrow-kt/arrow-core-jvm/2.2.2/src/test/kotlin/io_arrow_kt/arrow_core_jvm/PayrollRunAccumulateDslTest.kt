/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(ExperimentalRaiseAccumulateApi::class)

package io_arrow_kt.arrow_core_jvm

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.Raise
import arrow.core.raise.accumulate
import arrow.core.raise.accumulating
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNullOrAccumulate
import arrow.core.raise.ensureOrAccumulate
import arrow.core.raise.forEachAccumulating
import arrow.core.raise.getValue
import arrow.core.raise.mapValuesOrAccumulate
import arrow.core.right
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The experimental `accumulate {}` Raise DSL (`accumulating`, `bindOrAccumulate`,
 * `bindAllOrAccumulate`, `ensureOrAccumulate`, `ensureNotNullOrAccumulate` read through `by`
 * delegation) together with `mapValuesOrAccumulate` and `forEachAccumulating`.
 *
 * A payroll service turns the timesheet rows of a pay period into payslips. Every check on a
 * row records its failure and keeps going, so a payroll clerk sees every problem with a row in
 * one report. The delegated values are only read once all checks have run; reading a failed
 * one raises everything recorded so far. The whole batch is converted with
 * `mapValuesOrAccumulate`, and department budgets are checked with `forEachAccumulating`.
 */
class PayrollRunAccumulateDslTest {

    sealed interface PayrollError {
        data class InvalidHours(val employeeId: String, val raw: String) : PayrollError

        data class UnknownGrade(val employeeId: String, val grade: String) : PayrollError

        data class UnknownDeduction(val employeeId: String, val code: String) : PayrollError

        data class MissingBankAccount(val employeeId: String) : PayrollError

        data class NotOnRoster(val employeeId: String) : PayrollError

        data class OverBudget(val department: String, val grossCents: Int, val budgetCents: Int) :
            PayrollError
    }

    data class TimesheetRow(
        val employeeId: String,
        val department: String,
        val hours: String,
        val grade: String,
        val iban: String?,
        val deductionCodes: List<String>,
    )

    data class Payslip(
        val employeeId: String,
        val department: String,
        val iban: String,
        val grossCents: Int,
        val netCents: Int,
    )

    class PayrollService(
        private val activeRoster: Set<String>,
        private val departmentBudgetsCents: Map<String, Int>,
    ) {
        private val hourlyRateCentsByGrade: Map<String, Int> = mapOf("G1" to 2_000, "G2" to 3_500)
        private val deductionCentsByCode: Map<String, Int> = mapOf("PENSION" to 5_000, "TRANSIT" to 1_200)

        private fun Raise<PayrollError>.parseHours(row: TimesheetRow): Int {
            val hours: Int = row.hours.toIntOrNull()
                ?: raise(PayrollError.InvalidHours(row.employeeId, row.hours))
            ensure(hours in 0..200) { PayrollError.InvalidHours(row.employeeId, row.hours) }
            return hours
        }

        private fun hourlyRateCents(row: TimesheetRow): Either<PayrollError, Int> =
            hourlyRateCentsByGrade[row.grade]?.right()
                ?: PayrollError.UnknownGrade(row.employeeId, row.grade).left()

        private fun deductions(row: TimesheetRow): List<Either<PayrollError, Int>> =
            row.deductionCodes.map { code: String ->
                deductionCentsByCode[code]?.right()
                    ?: PayrollError.UnknownDeduction(row.employeeId, code).left()
            }

        /** Builds one payslip, reporting every problem with the row at once. */
        fun Raise<NonEmptyList<PayrollError>>.payslip(row: TimesheetRow): Payslip = accumulate {
            val hours: Int by accumulating { parseHours(row) }
            val rateCents: Int by hourlyRateCents(row).bindOrAccumulate()
            val deductionCents: List<Int> by deductions(row).bindAllOrAccumulate()
            val iban: String by ensureNotNullOrAccumulate(row.iban) {
                PayrollError.MissingBankAccount(row.employeeId)
            }
            ensureOrAccumulate(row.employeeId in activeRoster) {
                PayrollError.NotOnRoster(row.employeeId)
            }
            val gross: Int = hours * rateCents
            Payslip(row.employeeId, row.department, iban, gross, gross - deductionCents.sum())
        }

        /** Previews a single row the way the clerk's edit form does, without a batch. */
        fun preview(row: TimesheetRow): Either<NonEmptyList<PayrollError>, Payslip> =
            either { payslip(row) }

        /** Runs the whole pay period: every row first, then every department budget. */
        fun run(rows: List<TimesheetRow>): Either<NonEmptyList<PayrollError>, Map<String, Payslip>> =
            either {
                val rowsById: Map<String, TimesheetRow> = rows.associateBy { it.employeeId }
                val payslips: Map<String, Payslip> =
                    mapValuesOrAccumulate(rowsById) { entry: Map.Entry<String, TimesheetRow> ->
                        withNel { payslip(entry.value) }
                    }
                val byDepartment: Map<String, List<Payslip>> = payslips.values.groupBy { it.department }
                forEachAccumulating(byDepartment.entries) { entry: Map.Entry<String, List<Payslip>> ->
                    val gross: Int = entry.value.sumOf { it.grossCents }
                    val budget: Int = departmentBudgetsCents.getValue(entry.key)
                    ensure(gross <= budget) { PayrollError.OverBudget(entry.key, gross, budget) }
                }
                payslips
            }
    }

    private val service: PayrollService = PayrollService(
        activeRoster = setOf("E-1", "E-2", "E-3"),
        departmentBudgetsCents = mapOf("ops" to 500_000, "eng" to 600_000),
    )

    private val ada: TimesheetRow =
        TimesheetRow("E-1", "eng", "160", "G2", "DE89370400440532013000", listOf("PENSION"))
    private val grace: TimesheetRow =
        TimesheetRow("E-2", "ops", "120", "G1", "DE02120300000000202051", listOf("PENSION", "TRANSIT"))
    private val linus: TimesheetRow =
        TimesheetRow("E-3", "ops", "80", "G1", "DE12500105170648489890", emptyList())

    @Test
    fun cleanPayPeriodProducesEveryPayslip() {
        val result: Either<NonEmptyList<PayrollError>, Map<String, Payslip>> =
            service.run(listOf(ada, grace, linus))

        val expected: Map<String, Payslip> = mapOf(
            "E-1" to Payslip("E-1", "eng", "DE89370400440532013000", 560_000, 555_000),
            "E-2" to Payslip("E-2", "ops", "DE02120300000000202051", 240_000, 233_800),
            "E-3" to Payslip("E-3", "ops", "DE12500105170648489890", 160_000, 160_000),
        )
        assertEquals(expected.right(), result)
    }

    @Test
    fun brokenRowReportsEveryProblemInCheckOrder() {
        val broken = TimesheetRow("E-9", "ops", "lots", "G7", null, listOf("GYM", "PENSION", "PARKING"))

        val result: Either<NonEmptyList<PayrollError>, Payslip> = service.preview(broken)

        val expected: NonEmptyList<PayrollError> = nonEmptyListOf(
            PayrollError.InvalidHours("E-9", "lots"),
            PayrollError.UnknownGrade("E-9", "G7"),
            PayrollError.UnknownDeduction("E-9", "GYM"),
            PayrollError.UnknownDeduction("E-9", "PARKING"),
            PayrollError.MissingBankAccount("E-9"),
            PayrollError.NotOnRoster("E-9"),
        )
        assertEquals(expected.left(), result)
    }

    @Test
    fun rosterCheckAloneStillFailsAnOtherwiseValidRow() {
        val contractor: TimesheetRow = ada.copy(employeeId = "C-7")

        assertEquals(nonEmptyListOf(PayrollError.NotOnRoster("C-7")).left(), service.preview(contractor))
        assertEquals(
            Payslip("E-1", "eng", "DE89370400440532013000", 560_000, 555_000).right(),
            service.preview(ada),
        )
    }

    @Test
    fun batchCollectsErrorsAcrossRowsAndSkipsBudgetCheck() {
        val badHours: TimesheetRow = grace.copy(hours = "250")
        val noAccount: TimesheetRow = linus.copy(iban = null, grade = "G9")

        val result: Either<NonEmptyList<PayrollError>, Map<String, Payslip>> =
            service.run(listOf(ada, badHours, noAccount))

        val expected: NonEmptyList<PayrollError> = nonEmptyListOf(
            PayrollError.InvalidHours("E-2", "250"),
            PayrollError.UnknownGrade("E-3", "G9"),
            PayrollError.MissingBankAccount("E-3"),
        )
        assertEquals(expected.left(), result)
    }

    @Test
    fun overspentDepartmentsAreAllReported() {
        val tightBudgets = PayrollService(
            activeRoster = setOf("E-1", "E-2", "E-3"),
            departmentBudgetsCents = mapOf("eng" to 500_000, "ops" to 300_000),
        )

        val result: Either<NonEmptyList<PayrollError>, Map<String, Payslip>> =
            tightBudgets.run(listOf(ada, grace, linus))

        val expected: NonEmptyList<PayrollError> = nonEmptyListOf(
            PayrollError.OverBudget("eng", 560_000, 500_000),
            PayrollError.OverBudget("ops", 400_000, 300_000),
        )
        assertEquals(expected.left(), result)
    }
}
