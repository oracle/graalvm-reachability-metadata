/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_support;

import org.graalvm.internal.tck.NativeImageSupport;

final class SeleniumSupportNativeImageSupport {
    private static final String NATIVE_IMAGE_RUNTIME = "runtime";
    private static final String BYTE_BUDDY_DISPATCHER =
            "net.bytebuddy.utility.dispatcher.JavaDispatcher";
    private static final String BYTE_BUDDY_TYPE_DESCRIPTION =
            "net.bytebuddy.description.type.TypeDescription$ForLoadedType";

    private SeleniumSupportNativeImageSupport() {
    }

    static boolean isExpectedDecoratorFailure(Throwable throwable) {
        if (!NATIVE_IMAGE_RUNTIME.equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            if (current instanceof NoClassDefFoundError && current.getMessage() != null
                    && (current.getMessage().contains(BYTE_BUDDY_DISPATCHER)
                    || current.getMessage().contains(BYTE_BUDDY_TYPE_DESCRIPTION))) {
                return true;
            }
        }
        return false;
    }
}
