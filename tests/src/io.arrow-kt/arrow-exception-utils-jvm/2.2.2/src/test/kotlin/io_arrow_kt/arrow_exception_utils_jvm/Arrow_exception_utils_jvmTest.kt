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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CancellationException

public class Arrow_exception_utils_jvmTest {
    @Test
    fun nonFatalRecognizesOrdinaryExceptionsAndErrors() {
        assertThat(NonFatal(RuntimeException("runtime"))).isTrue()
        assertThat(NonFatal(IOException("io"))).isTrue()
        assertThat(NonFatal(IllegalArgumentException("argument"))).isTrue()
        assertThat(NonFatal(AssertionError("assertion"))).isTrue()
    }

    @Test
    fun nonFatalRejectsJvmFatalAndControlFlowThrowables() {
        assertThat(NonFatal(StackOverflowError("stack overflow"))).isFalse()
        assertThat(NonFatal(InternalError("internal vm error"))).isFalse()
        assertThat(NonFatal(ThreadDeath())).isFalse()
        assertThat(NonFatal(InterruptedException("interrupted"))).isFalse()
        assertThat(NonFatal(LinkageError("linkage"))).isFalse()
        assertThat(NonFatal(CancellationException("cancelled"))).isFalse()
    }

    @Test
    fun nonFatalOrThrowReturnsTheOriginalNonFatalThrowable() {
        val exception = IOException("recoverable failure")

        val result = exception.nonFatalOrThrow()

        assertThat(result).isSameAs(exception)
    }

    @Test
    fun nonFatalOrThrowRethrowsTheOriginalFatalThrowable() {
        val cancellation = CancellationException("cancelled")
        val linkageError = LinkageError("linkage")

        assertRethrowsSameThrowable(cancellation) {
            cancellation.nonFatalOrThrow()
        }
        assertRethrowsSameThrowable(linkageError) {
            linkageError.nonFatalOrThrow()
        }
    }

    @Test
    fun throwIfFatalAllowsNonFatalThrowableAndRethrowsFatalThrowable() {
        val nonFatal = IllegalStateException("ordinary failure")
        val fatal = InterruptedException("interrupted")

        nonFatal.throwIfFatal()

        assertRethrowsSameThrowable(fatal) {
            fatal.throwIfFatal()
        }
    }

    @Test
    fun nullableThrowableThrowIfNotNullOnlyThrowsWhenReceiverIsPresent() {
        val absent: Throwable? = null
        val present = IllegalArgumentException("present")

        absent.throwIfNotNull()

        assertRethrowsSameThrowable(present) {
            present.throwIfNotNull()
        }
    }

    @Test
    fun mergeSuppressedPreservesTheOnlyPresentThrowable() {
        val primary = IllegalStateException("primary")
        val secondary = IOException("secondary")
        val absent: Throwable? = null

        assertThat(absent.mergeSuppressed(null)).isNull()
        assertThat(primary.mergeSuppressed(null)).isSameAs(primary)
        assertThat(absent.mergeSuppressed(secondary)).isSameAs(secondary)
        assertThat(primary.suppressed).isEmpty()
        assertThat(secondary.suppressed).isEmpty()
    }

    @Test
    fun mergeSuppressedAddsNonFatalThrowableToPrimary() {
        val primary = IllegalStateException("primary")
        val secondary = IOException("secondary")

        val result = primary.mergeSuppressed(secondary)

        assertThat(result).isSameAs(primary)
        assertThat(primary.suppressed).containsExactly(secondary)
        assertThat(secondary.suppressed).isEmpty()
    }

    @Test
    fun mergeSuppressedAddsCancellationExceptionAsSuppressedInsteadOfThrowingIt() {
        val primary = IllegalStateException("primary")
        val cancellation = CancellationException("cancelled")

        val result = primary.mergeSuppressed(cancellation)

        assertThat(result).isSameAs(primary)
        assertThat(primary.suppressed).containsExactly(cancellation)
    }

    @Test
    fun mergeSuppressedRethrowsFatalNonCancellationThrowableWithoutMutatingPrimary() {
        val primary = IllegalStateException("primary")
        val fatal = LinkageError("fatal linkage")

        assertRethrowsSameThrowable(fatal) {
            primary.mergeSuppressed(fatal)
        }
        assertThat(primary.suppressed).isEmpty()
    }

    private fun assertRethrowsSameThrowable(expected: Throwable, block: () -> Unit) {
        val thrown = catchThrowable { block() }

        assertThat(thrown).isSameAs(expected)
    }
}
