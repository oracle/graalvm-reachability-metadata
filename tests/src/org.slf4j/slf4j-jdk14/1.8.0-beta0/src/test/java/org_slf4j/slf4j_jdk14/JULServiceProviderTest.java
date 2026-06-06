/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_jdk14;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Marker;
import org.slf4j.jul.JULServiceProvider;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class JULServiceProviderTest {

    @Test
    void serviceLoaderFindsAndInitializesJulProvider() {
        JULServiceProvider provider = ServiceLoader.load(SLF4JServiceProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(JULServiceProvider.class::isInstance)
                .map(JULServiceProvider.class::cast)
                .findFirst()
                .orElseThrow();

        provider.initialize();

        ILoggerFactory loggerFactory = provider.getLoggerFactory();
        IMarkerFactory markerFactory = provider.getMarkerFactory();
        MDCAdapter mdcAdapter = provider.getMDCAdapter();
        Marker marker = markerFactory.getMarker("coverage-marker");

        try {
            mdcAdapter.put("coverage-key", "coverage-value");

            assertThat(provider.getRequesteApiVersion()).isEqualTo(JULServiceProvider.REQUESTED_API_VERSION);
            assertThat(loggerFactory.getLogger("coverage-logger").getName()).isEqualTo("coverage-logger");
            assertThat(marker.getName()).isEqualTo("coverage-marker");
            assertThat(markerFactory.getMarker("coverage-marker")).isSameAs(marker);
            assertThat(mdcAdapter.get("coverage-key")).isEqualTo("coverage-value");
        } finally {
            mdcAdapter.remove("coverage-key");
        }
    }
}
