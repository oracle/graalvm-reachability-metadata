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
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class AuthenticatorTest {
    @Test
    void setsAuthenticatorOnHttpExporterBuilderDelegate() {
        HttpExporterBuilder<Marshaler> delegate =
                new HttpExporterBuilder<>(
                        "metadata-test-exporter",
                        "metadata-test-type",
                        "http://localhost:4318/v1/metadata-test");
        AuthenticatorDelegateHolder builder = new AuthenticatorDelegateHolder(delegate);
        Authenticator authenticator =
                () -> Collections.singletonMap("Authorization", "Bearer metadata-test-token");

        assertThatCode(() -> Authenticator.setAuthenticatorOnDelegate(builder, authenticator))
                .doesNotThrowAnyException();
    }
}

final class AuthenticatorDelegateHolder {
    @SuppressWarnings("unused")
    private final HttpExporterBuilder<?> delegate;

    AuthenticatorDelegateHolder(HttpExporterBuilder<?> delegate) {
        this.delegate = delegate;
    }
}
