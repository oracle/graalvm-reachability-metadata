/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_agent_jvm

import org.graalvm.internal.tck.NativeImageSupport

internal object MockkNativeImageSupport {
    private const val NATIVE_IMAGE_RUNTIME: String = "runtime"
    private const val BYTE_BUDDY_ATTACHMENT_PREFIX: String = "Error during attachment using:"
    private const val BYTE_BUDDY_NO_COMPATIBLE_PROVIDER: String = "No compatible attachment provider is available"

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
        System.getProperty("org.graalvm.nativeimage.imagecode") == NATIVE_IMAGE_RUNTIME

    private fun hasUnsupportedFeatureError(throwable: Throwable): Boolean =
        throwable.causeSequence().any { current: Throwable ->
            current is Error && NativeImageSupport.isUnsupportedFeatureError(current)
        }

    private fun hasByteBuddyAttachmentFailure(throwable: Throwable): Boolean =
        throwable.causeSequence().any { current: Throwable ->
            current is IllegalStateException && isByteBuddyAttachmentFailureMessage(current.message)
        }

    private fun hasJavaDispatcherInitializationFailure(throwable: Throwable): Boolean =
        throwable.causeSequence().any { current: Throwable ->
            current is NoClassDefFoundError &&
                current.message?.contains("net.bytebuddy.utility.dispatcher.JavaDispatcher") == true
        }

    private fun isByteBuddyAttachmentFailureMessage(message: String?): Boolean =
        message?.startsWith(BYTE_BUDDY_ATTACHMENT_PREFIX) == true ||
            message == BYTE_BUDDY_NO_COMPATIBLE_PROVIDER

    private fun Throwable.causeSequence(): Sequence<Throwable> =
        generateSequence(this) { current: Throwable -> current.cause }
}
