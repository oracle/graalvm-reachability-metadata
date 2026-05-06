/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_agent_jvm

import org.graalvm.internal.tck.NativeImageSupport

internal object MockkNativeImageSupport {
    fun isExpectedNativeImageFailure(throwable: Throwable): Boolean {
        if (!isNativeImageRuntime()) {
            return false
        }

        if (hasUnsupportedFeatureError(throwable)) {
            return true
        }

        return hasByteBuddyAttachmentFailure(throwable) || hasJavaDispatcherInitializationFailure(throwable)
    }

    private fun isNativeImageRuntime(): Boolean =
        System.getProperty("org.graalvm.nativeimage.imagecode") == "runtime"

    private fun hasUnsupportedFeatureError(throwable: Throwable): Boolean =
        throwable.causeSequence().any { current: Throwable ->
            current is Error && NativeImageSupport.isUnsupportedFeatureError(current)
        }

    private fun hasByteBuddyAttachmentFailure(throwable: Throwable): Boolean =
        throwable.causeSequence().any { current: Throwable ->
            current is IllegalStateException && current.message?.startsWith("Error during attachment using:") == true
        }

    private fun hasJavaDispatcherInitializationFailure(throwable: Throwable): Boolean =
        throwable.causeSequence().any { current: Throwable ->
            current is NoClassDefFoundError &&
                current.message?.contains("net.bytebuddy.utility.dispatcher.JavaDispatcher") == true
        }

    private fun Throwable.causeSequence(): Sequence<Throwable> =
        generateSequence(this) { current: Throwable -> current.cause }
}
