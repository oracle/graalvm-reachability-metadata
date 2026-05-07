/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_client;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.config.ClickHouseClientOption;
import org.junit.jupiter.api.Test;

public class ClickHouseClientOptionTest {
    private static final String VERSION_RESOURCE = "clickhouse-client-version.properties";
    private static final String UNKNOWN_VERSION = "unknown";

    @Test
    void readsProductVersionFromContextClassLoaderResource() {
        String version = ClickHouseClientOption.readVersionFromResource(VERSION_RESOURCE);

        assertThat(version).isNotBlank();
        assertThat(version).isNotEqualTo(UNKNOWN_VERSION);
    }

    @Test
    void fallsBackToClientOptionClassLoaderWhenContextClassLoaderCannotFindResource() {
        String version = readVersionWithContextClassLoader(ClassLoader.getPlatformClassLoader());

        assertThat(version).isNotBlank();
        assertThat(version).isNotEqualTo(UNKNOWN_VERSION);
    }

    private static String readVersionWithContextClassLoader(ClassLoader contextClassLoader) {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(contextClassLoader);
        try {
            return ClickHouseClientOption.readVersionFromResource(VERSION_RESOURCE);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }
}
