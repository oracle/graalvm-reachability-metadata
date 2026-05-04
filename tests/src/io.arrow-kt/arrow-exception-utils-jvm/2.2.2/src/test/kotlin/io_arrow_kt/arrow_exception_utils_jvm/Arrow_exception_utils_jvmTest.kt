/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_exception_utils_jvm

import arrow.core.NonFatal
import arrow.core.mergeSuppressed
import arrow.core.nonFatalOrThrow
import arrow.core.throwIfFatal
import arrow.core.throwIfNotNull
import java.util.concurrent.CancellationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class ArrowExceptionUtilsJvmTest {
    @Test
    fun `NonFatal identifies ordinary failures as recoverable`() {
        val exceptions: List<Throwable> = listOf(
            IllegalArgumentException("bad argument"),
            IllegalStateException("bad state"),
            RuntimeException("runtime"),
            Exception("checked"),
            AssertionError("assertion"),
            Error("plain error"),
        )

        exceptions.forEach { exception: Throwable ->
            assertThat(NonFatal(exception)).isTrue()
        }
    }

    @Test
    fun `NonFatal identifies fatal platform failures`() {
        val fatalFailures: List<Throwable> = listOf(
            OutOfMemoryError("virtual machine error"),
            ThreadDeath(),
            InterruptedException("interrupted"),
            LinkageError("linkage"),
            CancellationException("cancelled"),
        )

        fatalFailures.forEach { failure: Throwable ->
            assertThat(NonFatal(failure)).isFalse()
        }
    }

    @Test
    fun `nonFatalOrThrow returns the same non-fatal throwable`() {
        val failure: IllegalArgumentException = IllegalArgumentException("validation failed")

        val result: Throwable = failure.nonFatalOrThrow()

        assertThat(result).isSameAs(failure)
    }

    @Test
    fun `nonFatalOrThrow rethrows fatal throwables unchanged`() {
        val fatal: LinkageError = LinkageError("native dependency mismatch")

        assertThatThrownBy { fatal.nonFatalOrThrow() }
            .isSameAs(fatal)
    }

    @Test
    fun `throwIfFatal ignores non-fatal throwables`() {
        val nonFatal: IllegalStateException = IllegalStateException("recoverable")

        assertThatCode { nonFatal.throwIfFatal() }
            .doesNotThrowAnyException()
    }

    @Test
    fun `throwIfFatal rethrows fatal throwables unchanged`() {
        val fatal: InterruptedException = InterruptedException("stop work")

        assertThatThrownBy { fatal.throwIfFatal() }
            .isSameAs(fatal)
    }

    @Test
    fun `throwIfNotNull does nothing for null`() {
        val failure: Throwable? = null

        assertThatCode { failure.throwIfNotNull() }
            .doesNotThrowAnyException()
    }

    @Test
    fun `throwIfNotNull rethrows the supplied throwable unchanged`() {
        val failure: IllegalArgumentException = IllegalArgumentException("present")

        assertThatThrownBy { failure.throwIfNotNull() }
            .isSameAs(failure)
    }

    @Test
    fun `mergeSuppressed returns null when both throwables are null`() {
        val failure: Throwable? = null

        val result: Throwable? = failure.mergeSuppressed(null)

        assertThat(result).isNull()
    }

    @Test
    fun `mergeSuppressed returns the only supplied throwable`() {
        val primary: IllegalArgumentException = IllegalArgumentException("primary")
        val secondary: IllegalStateException = IllegalStateException("secondary")

        val noPrimary: Throwable? = null

        assertThat(primary.mergeSuppressed(null)).isSameAs(primary)
        assertThat(noPrimary.mergeSuppressed(secondary)).isSameAs(secondary)
        assertThat(primary.suppressed).isEmpty()
        assertThat(secondary.suppressed).isEmpty()
    }

    @Test
    fun `mergeSuppressed adds a non-fatal secondary throwable to the primary`() {
        val primary: IllegalArgumentException = IllegalArgumentException("primary")
        val secondary: IllegalStateException = IllegalStateException("secondary")

        val result: Throwable? = primary.mergeSuppressed(secondary)

        assertThat(result).isSameAs(primary)
        assertThat(primary.suppressed).containsExactly(secondary)
    }

    @Test
    fun `mergeSuppressed accumulates cleanup failures starting from no primary failure`() {
        var accumulated: Throwable? = null
        val firstCleanupFailure: IllegalStateException = IllegalStateException("first cleanup")
        val secondCleanupFailure: RuntimeException = RuntimeException("second cleanup")

        accumulated = accumulated mergeSuppressed firstCleanupFailure
        accumulated = accumulated mergeSuppressed secondCleanupFailure

        assertThat(accumulated).isSameAs(firstCleanupFailure)
        assertThat(firstCleanupFailure.suppressed).containsExactly(secondCleanupFailure)
        assertThat(secondCleanupFailure.suppressed).isEmpty()
    }

    @Test
    fun `mergeSuppressed preserves existing suppressed failures and appends the next one`() {
        val primary: IllegalArgumentException = IllegalArgumentException("primary")
        val existing: IllegalStateException = IllegalStateException("existing")
        val secondary: RuntimeException = RuntimeException("secondary")
        primary.addSuppressed(existing)

        val result: Throwable? = primary.mergeSuppressed(secondary)

        assertThat(result).isSameAs(primary)
        assertThat(primary.suppressed).containsExactly(existing, secondary)
    }

    @Test
    fun `mergeSuppressed adds cancellation as suppressed even though cancellation is fatal`() {
        val primary: IllegalArgumentException = IllegalArgumentException("primary")
        val cancellation: CancellationException = CancellationException("cancelled")

        val result: Throwable? = primary.mergeSuppressed(cancellation)

        assertThat(result).isSameAs(primary)
        assertThat(primary.suppressed).containsExactly(cancellation)
    }

    @Test
    fun `mergeSuppressed ignores self-suppression`() {
        val failure: IllegalArgumentException = IllegalArgumentException("same failure")

        val result: Throwable? = failure.mergeSuppressed(failure)

        assertThat(result).isSameAs(failure)
        assertThat(failure.suppressed).isEmpty()
    }

    @Test
    fun `mergeSuppressed rethrows fatal secondary failures unchanged`() {
        val primary: IllegalArgumentException = IllegalArgumentException("primary")
        val fatal: LinkageError = LinkageError("linkage")

        assertThatThrownBy { primary.mergeSuppressed(fatal) }
            .isSameAs(fatal)
        assertThat(primary.suppressed).isEmpty()
    }
}
