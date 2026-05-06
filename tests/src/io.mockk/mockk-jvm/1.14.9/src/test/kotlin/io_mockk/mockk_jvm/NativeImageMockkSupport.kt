/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_mockk.mockk_jvm

import org.graalvm.internal.tck.NativeImageSupport

private const val GATEWAY_INITIALIZATION_FAILURE = "Could not initialize class io.mockk.impl.JvmMockKGateway"

internal fun isUnsupportedMockkNativeImageFailure(throwable: Throwable): Boolean =
    NativeImageSupport.isUnsupportedFeature(throwable) ||
        (throwable is NoClassDefFoundError && throwable.message == GATEWAY_INITIALIZATION_FAILURE)
