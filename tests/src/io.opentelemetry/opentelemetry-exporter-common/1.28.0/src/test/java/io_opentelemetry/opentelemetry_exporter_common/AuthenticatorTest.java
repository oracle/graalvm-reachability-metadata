/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_common;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.opentelemetry.exporter.internal.auth.Authenticator;
import io.opentelemetry.exporter.internal.http.HttpExporterBuilder;
import io.opentelemetry.exporter.internal.marshal.Marshaler;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AuthenticatorTest {
    @Test
    void setsAuthenticatorOnHttpExporterBuilderDelegate() {
        Authenticator authenticator = () -> Map.of("Authorization", "Bearer test-token");
        DelegatingHttpExporterBuilder builder = new DelegatingHttpExporterBuilder();

        assertThatCode(() -> Authenticator.setAuthenticatorOnDelegate(builder, authenticator))
                .doesNotThrowAnyException();
    }
}

final class DelegatingHttpExporterBuilder {
    private final HttpExporterBuilder<Marshaler> delegate =
            new HttpExporterBuilder<>("test-exporter", "span", "http://localhost:4318/v1/traces");
}
