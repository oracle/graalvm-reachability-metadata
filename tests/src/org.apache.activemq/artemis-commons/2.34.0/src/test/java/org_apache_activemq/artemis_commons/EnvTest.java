/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.activemq.artemis.utils.Env;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import sun.misc.Unsafe;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EnvTest {
    private static final String SHARED_UNSAFE_FIELD_NAME = "theUnsafe";

    @Test
    @Order(1)
    public void reportsOsPageSizeFromRuntimeEnvironment() {
        int osPageSize = Env.osPageSize();

        assertThat(osPageSize).isPositive();
        assertThat(Integer.bitCount(osPageSize)).isEqualTo(1);
    }

    @Test
    @Order(2)
    public void fallsBackToUnsafeConstructorWhenSharedUnsafeFieldCannotBeRead() throws Exception {
        try {
            int osPageSize = isolatedEnvOsPageSizeWithWrongSharedUnsafeType();

            assertThat(osPageSize).isPositive();
            assertThat(Integer.bitCount(osPageSize)).isEqualTo(1);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    @Order(3)
    public void togglesTestEnvironmentFlag() {
        boolean originalValue = Env.isTestEnv();
        try {
            Env.setTestEnv(true);
            assertThat(Env.isTestEnv()).isTrue();

            Env.setTestEnv(false);
            assertThat(Env.isTestEnv()).isFalse();
        } finally {
            Env.setTestEnv(originalValue);
        }
    }

    @Test
    @Order(4)
    public void identifiesCurrentOperatingSystemFamily() {
        boolean linux = Env.isLinuxOs();
        boolean mac = Env.isMacOs();
        boolean windows = Env.isWindowsOs();

        assertThat(linux || mac || windows).isEqualTo(isKnownOperatingSystem());
        assertThat((linux ? 1 : 0) + (mac ? 1 : 0) + (windows ? 1 : 0)).isLessThanOrEqualTo(1);
    }

    private static int isolatedEnvOsPageSizeWithWrongSharedUnsafeType() throws Exception {
        URL artemisCommonsLocation = Env.class.getProtectionDomain().getCodeSource().getLocation();
        SharedUnsafeField sharedUnsafeField = replaceSharedUnsafeWithWrongType();
        try (URLClassLoader classLoader = new IsolatedEnvClassLoader(artemisCommonsLocation)) {
            Class<?> envClass = Class.forName(Env.class.getName(), true, classLoader);
            Method osPageSize = envClass.getMethod("osPageSize");
            return (Integer) osPageSize.invoke(null);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof Exception checkedException) {
                throw checkedException;
            }
            throw exception;
        } finally {
            sharedUnsafeField.restore();
        }
    }

    private static SharedUnsafeField replaceSharedUnsafeWithWrongType() throws Exception {
        Unsafe unsafe = unsafe();
        Field unsafeField = Unsafe.class.getDeclaredField(SHARED_UNSAFE_FIELD_NAME);
        Object staticFieldBase = unsafe.staticFieldBase(unsafeField);
        long staticFieldOffset = unsafe.staticFieldOffset(unsafeField);
        Object originalValue = unsafe.getObject(staticFieldBase, staticFieldOffset);

        unsafe.putObject(staticFieldBase, staticFieldOffset, "not an Unsafe instance");
        return () -> unsafe.putObject(staticFieldBase, staticFieldOffset, originalValue);
    }

    private static Unsafe unsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField(SHARED_UNSAFE_FIELD_NAME);
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    private static boolean isKnownOperatingSystem() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.startsWith("linux") || osName.startsWith("mac") || osName.startsWith("windows");
    }

    private interface SharedUnsafeField {
        void restore();
    }

    private static final class IsolatedEnvClassLoader extends URLClassLoader {
        private IsolatedEnvClassLoader(URL artemisCommonsLocation) {
            super(new URL[] {artemisCommonsLocation}, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!Env.class.getName().equals(name)) {
                return super.loadClass(name, resolve);
            }

            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null) {
                loadedClass = findClass(name);
            }
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }
}
