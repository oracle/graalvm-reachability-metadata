/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.exporter.internal.auth.Authenticator;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class AuthenticatorTest {
    private static final String UNSUPPORTED_DELEGATE_MESSAGE =
            "Delegate field is not type DefaultGrpcExporterBuilder or OkHttpGrpcExporterBuilder.";

    @Test
    void rejectsUnsupportedDelegateType() {
        Authenticator authenticator = Collections::emptyMap;
        BuilderWithUnsupportedDelegate builder = new BuilderWithUnsupportedDelegate();

        assertThatThrownBy(() -> Authenticator.setAuthenticatorOnDelegate(builder, authenticator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(UNSUPPORTED_DELEGATE_MESSAGE);
    }

    private static final class BuilderWithUnsupportedDelegate {
        @SuppressWarnings("unused")
        private final Object delegate = new Object();
    }
}
