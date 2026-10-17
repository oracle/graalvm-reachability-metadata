/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.raise.mapOrAccumulate
import arrow.core.raise.zipOrAccumulate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Error accumulation in the `Raise` DSL with `zipOrAccumulate` and `mapOrAccumulate`.
 *
 * A sign-up service validates each submitted form field by field. Instead of stopping at the
 * first bad field it reports every problem at once, so the user can fix the whole form in one
 * round trip. Each field validator is ordinary `Raise` code that short-circuits on its own
 * first failure; `zipOrAccumulate` runs all of them and collects their errors into a
 * `NonEmptyList`. A bulk import of invitation rows reuses the same validators through
 * `mapOrAccumulate`, tagging every failing row with its line number.
 */
class SignupFormAccumulatedValidationTest {

    sealed interface FieldError {
        val field: String

        data class Blank(override val field: String) : FieldError

        data class TooShort(override val field: String, val minLength: Int) : FieldError

        data class Malformed(override val field: String, val value: String) : FieldError

        data class OutOfRange(override val field: String, val value: Int) : FieldError
    }

    data class RowError(val line: Int, val errors: NonEmptyList<FieldError>)

    data class SignupForm(
        val username: String,
        val email: String,
        val password: String,
        val age: String,
    )

    data class Account(val username: String, val email: String, val age: Int)

    data class Invitation(val email: String, val age: Int)

    private fun Raise<FieldError>.username(raw: String): String {
        val trimmed: String = raw.trim()
        ensure(trimmed.isNotEmpty()) { FieldError.Blank("username") }
        ensure(trimmed.length >= 3) { FieldError.TooShort("username", 3) }
        return trimmed.lowercase()
    }

    private fun Raise<FieldError>.email(raw: String): String {
        val trimmed: String = raw.trim()
        ensure(trimmed.isNotEmpty()) { FieldError.Blank("email") }
        val at: Int = trimmed.indexOf('@')
        ensure(at > 0 && trimmed.indexOf('.', at) > at + 1) {
            FieldError.Malformed("email", trimmed)
        }
        return trimmed.lowercase()
    }

    private fun Raise<FieldError>.password(raw: String) {
        ensure(raw.isNotEmpty()) { FieldError.Blank("password") }
        ensure(raw.length >= 8) { FieldError.TooShort("password", 8) }
    }

    private fun Raise<FieldError>.age(raw: String): Int {
        val parsed: Int =
            ensureNotNull(raw.trim().toIntOrNull()) { FieldError.Malformed("age", raw) }
        ensure(parsed in 13..120) { FieldError.OutOfRange("age", parsed) }
        return parsed
    }

    private fun register(form: SignupForm): EitherNel<FieldError, Account> = either {
        zipOrAccumulate(
            { username(form.username) },
            { email(form.email) },
            { password(form.password) },
            { age(form.age) },
        ) { username: String, email: String, _: Unit, age: Int -> Account(username, email, age) }
    }

    private fun importInvitations(
        rows: List<String>,
    ): Either<NonEmptyList<RowError>, List<Invitation>> = either {
        mapOrAccumulate(rows.withIndex()) { (index: Int, row: String) ->
            val columns: List<String> = row.split(',')
            val rowResult: EitherNel<FieldError, Invitation> = either {
                zipOrAccumulate(
                    { email(columns.getOrElse(0) { "" }) },
                    { age(columns.getOrElse(1) { "" }) },
                ) { email: String, age: Int -> Invitation(email, age) }
            }
            rowResult
                .mapLeft { errors: NonEmptyList<FieldError> -> RowError(index + 1, errors) }
                .bind()
        }
    }

    @Test
    fun validFormProducesNormalizedAccount() {
        val result: EitherNel<FieldError, Account> =
            register(SignupForm("  Ada_L ", "Ada@Example.org", "correct horse", "36"))

        assertEquals(Either.Right(Account("ada_l", "ada@example.org", 36)), result)
    }

    @Test
    fun everyInvalidFieldIsReportedInDeclarationOrder() {
        val result: EitherNel<FieldError, Account> =
            register(SignupForm("al", "not-an-email", "short", "eleven"))

        val errors: NonEmptyList<FieldError> =
            checkNotNull(result.leftOrNull()) { "Expected rejected form, got $result" }
        assertEquals(
            nonEmptyListOf(
                FieldError.TooShort("username", 3),
                FieldError.Malformed("email", "not-an-email"),
                FieldError.TooShort("password", 8),
                FieldError.Malformed("age", "eleven"),
            ),
            errors,
        )
        assertEquals(
            listOf("username", "email", "password", "age"),
            errors.map { error: FieldError -> error.field },
        )
    }

    @Test
    fun eachFieldStopsAtItsFirstFailureWhileOthersStillRun() {
        val result: EitherNel<FieldError, Account> =
            register(SignupForm("   ", "jo@site.io", "", "7"))

        assertEquals(
            Either.Left(
                nonEmptyListOf(
                    FieldError.Blank("username"),
                    FieldError.Blank("password"),
                    FieldError.OutOfRange("age", 7),
                ),
            ),
            result,
        )
    }

    @Test
    fun bulkImportAcceptsCleanRows() {
        val result: Either<NonEmptyList<RowError>, List<Invitation>> =
            importInvitations(listOf("grace@navy.mil,45", " Linus@Kernel.org ,28"))

        assertEquals(
            Either.Right(
                listOf(Invitation("grace@navy.mil", 45), Invitation("linus@kernel.org", 28)),
            ),
            result,
        )
    }

    @Test
    fun bulkImportReportsEveryFailingRowWithItsLineNumber() {
        val rows: List<String> = listOf(
            "grace@navy.mil,45",
            "broken,200",
            "ken@bell-labs.com,51",
            ",x",
        )

        val errors: NonEmptyList<RowError> = importInvitations(rows).fold(
            { rowErrors: NonEmptyList<RowError> -> rowErrors },
            { invitations: List<Invitation> -> error("Expected rejected rows, got $invitations") },
        )

        assertEquals(
            nonEmptyListOf(
                RowError(
                    2,
                    nonEmptyListOf(
                        FieldError.Malformed("email", "broken"),
                        FieldError.OutOfRange("age", 200),
                    ),
                ),
                RowError(
                    4,
                    nonEmptyListOf(FieldError.Blank("email"), FieldError.Malformed("age", "x")),
                ),
            ),
            errors,
        )
        assertEquals(listOf(2, 4), errors.map { rowError: RowError -> rowError.line })
    }
}
