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
    @Test
    void installsAuthenticatorOnHttpDelegate() {
        Authenticator authenticator = () -> Collections.singletonMap("authorization", "Bearer token");
        HttpDelegateBuilder builder = new HttpDelegateBuilder();

        assertThatCode(() -> Authenticator.setAuthenticatorOnDelegate(builder, authenticator))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBuilderWithoutDelegateField() {
        Authenticator authenticator = Collections::emptyMap;

        assertThatThrownBy(() -> Authenticator.setAuthenticatorOnDelegate(new Object(), authenticator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unable to access delegate reflectively.");
    }

    private static final class HttpDelegateBuilder {
        private final HttpExporterBuilder<Marshaler> delegate =
                new HttpExporterBuilder<>("test", "signal", "http://localhost:4318/v1/traces");
    }
}
