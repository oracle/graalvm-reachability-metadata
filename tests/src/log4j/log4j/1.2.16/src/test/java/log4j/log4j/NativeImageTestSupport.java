/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Assumptions;

final class NativeImageTestSupport {

    private NativeImageTestSupport() {
    }

    static void assumeDesktopToolkitAvailable() {
        Assumptions.assumeFalse(isNativeImageRuntime(), "Desktop toolkit classes are not supported in native-image tests");
    }

    private static boolean isNativeImageRuntime() {
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            return true;
        }
        try {
            Class<?> imageInfoClass = Class.forName("org.graalvm.nativeimage.ImageInfo");
            Method inImageRuntimeCode = imageInfoClass.getMethod("inImageRuntimeCode");
            return Boolean.TRUE.equals(inImageRuntimeCode.invoke(null));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
