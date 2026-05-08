/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.BindException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.runtime.QuarkusBindException;
import io.quarkus.runtime.ResettableSystemProperties;

public class RuntimeUtilitiesTest {
    @Test
    void resettableSystemPropertiesRestoresPreviousValuesAndClearsNewValues() {
        String existingKey = "io.quarkus.test.resettable.existing";
        String newKey = "io.quarkus.test.resettable.new";
        String previousExistingValue = System.getProperty(existingKey);
        String previousNewValue = System.getProperty(newKey);

        try {
            System.setProperty(existingKey, "before");
            System.clearProperty(newKey);

            try (ResettableSystemProperties ignored = new ResettableSystemProperties(Map.of(
                    existingKey, "during",
                    newKey, "created"))) {
                assertThat(System.getProperty(existingKey)).isEqualTo("during");
                assertThat(System.getProperty(newKey)).isEqualTo("created");
            }

            assertThat(System.getProperty(existingKey)).isEqualTo("before");
            assertThat(System.getProperty(newKey)).isNull();
        } finally {
            restoreProperty(existingKey, previousExistingValue);
            restoreProperty(newKey, previousNewValue);
        }
    }

    @Test
    void resettableSystemPropertiesFactoryMethodsHandleSingleAndEmptyScopes() {
        String key = "io.quarkus.test.resettable.single";
        String previousValue = System.getProperty(key);

        try {
            System.clearProperty(key);

            try (ResettableSystemProperties ignored = ResettableSystemProperties.of(key, "temporary")) {
                assertThat(System.getProperty(key)).isEqualTo("temporary");
            }
            assertThat(System.getProperty(key)).isNull();

            System.setProperty(key, "unchanged");
            try (ResettableSystemProperties ignored = ResettableSystemProperties.empty()) {
                assertThat(System.getProperty(key)).isEqualTo("unchanged");
            }
            assertThat(System.getProperty(key)).isEqualTo("unchanged");
        } finally {
            restoreProperty(key, previousValue);
        }
    }

    @Test
    void quarkusBindExceptionFormatsKnownAndUnknownHosts() {
        BindException cause = new BindException("Address already in use");
        QuarkusBindException knownHost = new QuarkusBindException("127.0.0.1", 8080, cause);
        QuarkusBindException unknownHost = new QuarkusBindException("example.invalid", 9090, cause);

        assertThat(QuarkusBindException.isKnownHost("localhost")).isTrue();
        assertThat(QuarkusBindException.isKnownHost("127.0.0.1")).isTrue();
        assertThat(QuarkusBindException.isKnownHost("0.0.0.0")).isTrue();
        assertThat(QuarkusBindException.isKnownHost("example.invalid")).isFalse();

        assertThat(knownHost.getHost()).isEqualTo("127.0.0.1");
        assertThat(knownHost.getPort()).isEqualTo(8080);
        assertThat(knownHost.getMessage()).isEqualTo("Port already bound: 8080: Address already in use");

        assertThat(unknownHost.getHost()).isEqualTo("example.invalid");
        assertThat(unknownHost.getPort()).isEqualTo(9090);
        assertThat(unknownHost.getMessage())
                .isEqualTo("Unable to bind to host: example.invalid and port: 9090: Address already in use");
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
