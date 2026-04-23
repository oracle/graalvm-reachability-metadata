/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.c3p0.ConnectionTester;
import com.mchange.v2.c3p0.impl.DefaultConnectionTester;
import com.mchange.v2.log.MLog;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import static org.assertj.core.api.Assertions.assertThat;

public class C3P0RegistryTest {
    @Test
    void createsRegistryManagedStrategiesByClassName() throws Exception {
        ConnectionTester defaultTester = C3P0Registry.getDefaultConnectionTester();
        ConnectionTester customTester = C3P0Registry.getConnectionTester(RegistryConnectionTester.class.getName());

        assertThat(defaultTester).isInstanceOf(DefaultConnectionTester.class);
        assertThat(customTester)
            .isInstanceOf(RegistryConnectionTester.class)
            .isSameAs(C3P0Registry.getConnectionTester(RegistryConnectionTester.class.getName()))
            .isNotSameAs(defaultTester);
        assertThat(C3P0Registry.getConnectionTester("missing.ConnectionTester"))
            .isInstanceOf(DefaultConnectionTester.class);
        assertThat(C3P0Registry.getConnectionCustomizer(TrackingConnectionCustomizer.class.getName()))
            .isInstanceOf(TrackingConnectionCustomizer.class);
    }

    @Test
    void initializesRegistryWithDefaultManagementCoordinatorWhenNoPropertiesResourceIsVisible() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousContextClassLoader = thread.getContextClassLoader();

        try (URLClassLoader classLoader = new URLClassLoader(
            new URL[] {
                codeSourceUrl(C3P0Registry.class),
                codeSourceUrl(MLog.class)
            },
            ClassLoader.getPlatformClassLoader()
        )) {
            thread.setContextClassLoader(classLoader);
            Class<?> isolatedRegistry = Class.forName(C3P0Registry.class.getName(), true, classLoader);

            assertThat(isolatedRegistry).isNotSameAs(C3P0Registry.class);
            assertThat(isolatedRegistry.getClassLoader()).isSameAs(classLoader);
            assertThat(classLoader.getResource("c3p0.properties")).isNull();
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }
}
