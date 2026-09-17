/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security_oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.security.oauth2.endpoint.authorization.state.DefaultState;
import io.micronaut.security.oauth2.endpoint.authorization.state.State;
import io.micronaut.security.oauth2.endpoint.authorization.state.StateSerDes;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration coverage for OAuth authorization state serialization. */
@Timeout(55)
public class JacksonStateSerDesTest {
    @Test
    void stateSerDesBeanRoundTripsAuthorizationState() {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            StateSerDes stateSerDes = context.getBean(StateSerDes.class);
            DefaultState original = new DefaultState();
            original.setNonce("authorization-state-nonce");
            original.setRedirectUri(URI.create("https://app.example.test/orders/42"));

            String encoded = stateSerDes.serialize(original);
            State decoded = stateSerDes.deserialize(encoded);

            assertThat(encoded).isNotBlank();
            assertThat(decoded).isInstanceOf(DefaultState.class);
            assertThat(decoded.getNonce()).isEqualTo("authorization-state-nonce");
            assertThat(decoded.getRedirectUri())
                    .isEqualTo(URI.create("https://app.example.test/orders/42"));
        }
    }
}
