/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.exporter.internal.auth.Authenticator;
import io.opentelemetry.exporter.internal.http.HttpExporterBuilder;
import io.opentelemetry.exporter.internal.marshal.Marshaler;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class AuthenticatorTest {
    private final Authenticator authenticator = Collections::emptyMap;

    @Test
    void setsAuthenticatorOnHttpExporterDelegate() {
        DelegatingBuilder builder =
                new DelegatingBuilder(
                        new HttpExporterBuilder<Marshaler>(
                                "test-exporter", "telemetry", "http://localhost:4318/v1/test"));

        assertThatCode(() -> Authenticator.setAuthenticatorOnDelegate(builder, authenticator))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDelegateWithUnsupportedType() {
        DelegatingBuilder builder = new DelegatingBuilder(new Object());

        assertThatThrownBy(() -> Authenticator.setAuthenticatorOnDelegate(builder, authenticator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Delegate field is not type");
    }

    private static final class DelegatingBuilder {
        private final Object delegate;

        private DelegatingBuilder(Object delegate) {
            this.delegate = delegate;
        }
    }
}
