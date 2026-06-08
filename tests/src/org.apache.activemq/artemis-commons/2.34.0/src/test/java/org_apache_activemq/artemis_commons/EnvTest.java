/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.apache.activemq.artemis.utils.Env;
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
    public void fallsBackToUnsafeConstructorWhenSharedUnsafeFieldCannotBeRead() throws Exception {
        SharedUnsafeField sharedUnsafeField = replaceSharedUnsafeWithWrongType();
        try {
            int osPageSize = Env.osPageSize();

            assertThat(osPageSize).isPositive();
            assertThat(Integer.bitCount(osPageSize)).isEqualTo(1);
        } finally {
            sharedUnsafeField.restore();
        }
    }

    @Test
    @Order(2)
    public void reportsOsPageSizeFromRuntimeEnvironment() {
        int osPageSize = Env.osPageSize();

        assertThat(osPageSize).isPositive();
        assertThat(Integer.bitCount(osPageSize)).isEqualTo(1);
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
}
