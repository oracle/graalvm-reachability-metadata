/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import org.conscrypt.Conscrypt;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

public class NativeLibraryLoaderTest {
    private static final String CONSCRYPT_CLASS_NAME = "org.conscrypt.Conscrypt";
    private static final String NATIVE_LIBRARY_LOADER_CLASS_NAME =
            "org.conscrypt.NativeLibraryLoader";
    private static final String NATIVE_LIBRARY_UTIL_CLASS_NAME =
            "org.conscrypt.NativeLibraryUtil";

    @Test
    @ResourceLock("system-properties")
    void unavailableIsolatedProviderChecksNativeResourcesAndHelperClassBytes() throws Exception {
        String originalOsName = System.getProperty("os.name");
        String originalOsArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.name", "Mac OS X");
            System.setProperty("os.arch", "conscrypt-test-unknown");
            try (DenyNativeLibraryUtilLookupClassLoader classLoader =
                    new DenyNativeLibraryUtilLookupClassLoader(conscryptCodeSource())) {
                Class<?> conscryptClass = Class.forName(CONSCRYPT_CLASS_NAME, true, classLoader);
                Method checkAvailability = conscryptClass.getMethod("checkAvailability");

                try {
                    checkAvailability.invoke(null);
                    throw new AssertionError("Expected Conscrypt to be unavailable");
                } catch (InvocationTargetException exception) {
                    assertUnavailableOrUnsupportedDynamicClassLoading(exception.getCause());
                    assertThat(classLoader.deniedNativeLibraryUtilLookups()).isPositive();
                }
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        } finally {
            restoreProperty("os.name", originalOsName);
            restoreProperty("os.arch", originalOsArch);
        }
    }

    private static URL[] conscryptCodeSource() {
        CodeSource codeSource = Conscrypt.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return new URL[] {codeSource.getLocation()};
    }

    private static void restoreProperty(String propertyName, String originalValue) {
        if (originalValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, originalValue);
        }
    }

    private static void assertUnavailableOrUnsupportedDynamicClassLoading(Throwable throwable) {
        if (throwable instanceof Error error
                && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        assertThat(throwable).isInstanceOf(UnsatisfiedLinkError.class);
    }

    private static void rethrowUnlessUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class DenyNativeLibraryUtilLookupClassLoader extends URLClassLoader {
        private int deniedNativeLibraryUtilLookups;

        private DenyNativeLibraryUtilLookupClassLoader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (NATIVE_LIBRARY_UTIL_CLASS_NAME.equals(name) && isExplicitHelperLookup()) {
                deniedNativeLibraryUtilLookups++;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }

        private int deniedNativeLibraryUtilLookups() {
            return deniedNativeLibraryUtilLookups;
        }

        private static boolean isExplicitHelperLookup() {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                if (NATIVE_LIBRARY_LOADER_CLASS_NAME.equals(element.getClassName())
                        && "tryToLoadClass".equals(element.getMethodName())) {
                    return true;
                }
            }
            return false;
        }
    }
}
