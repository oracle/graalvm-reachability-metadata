/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_jdk14;

import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Marker;
import org.slf4j.jul.JDK14LoggerFactory;
import org.slf4j.jul.JULServiceProvider;
import org.slf4j.spi.MDCAdapter;

import static org.assertj.core.api.Assertions.assertThat;

public class JULServiceProviderTest {

    @Test
    void initializeExposesJulLoggerFactory() {
        JULServiceProvider provider = initializedProvider();

        ILoggerFactory loggerFactory = provider.getLoggerFactory();

        assertThat(loggerFactory).isInstanceOf(JDK14LoggerFactory.class);
        assertThat(loggerFactory.getLogger("coverage-logger").getName()).isEqualTo("coverage-logger");
    }

    @Test
    void initializeExposesBasicMarkerFactory() {
        JULServiceProvider provider = initializedProvider();

        IMarkerFactory markerFactory = provider.getMarkerFactory();
        Marker marker = markerFactory.getMarker("coverage-marker");

        assertThat(markerFactory).isNotNull();
        assertThat(marker.getName()).isEqualTo("coverage-marker");
        assertThat(markerFactory.getMarker("coverage-marker")).isSameAs(marker);
    }

    @Test
    void initializeExposesBasicMdcAdapter() {
        JULServiceProvider provider = initializedProvider();
        MDCAdapter adapter = provider.getMDCAdapter();
        String key = "coverage-mdc-key";

        assertThat(adapter).isNotNull();
        try {
            adapter.put(key, "coverage-mdc-value");

            assertThat(adapter.get(key)).isEqualTo("coverage-mdc-value");
        } finally {
            adapter.remove(key);
        }
    }

    private static JULServiceProvider initializedProvider() {
        JULServiceProvider provider = new JULServiceProvider();
        provider.initialize();
        return provider;
    }
}
