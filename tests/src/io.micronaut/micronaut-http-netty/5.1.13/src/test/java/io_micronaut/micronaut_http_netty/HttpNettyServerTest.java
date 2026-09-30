/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_http_netty;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class HttpNettyServerTest {
    @Test
    @Timeout(55)
    void startsAnEmbeddedNettyServer() {
        Map<String, Object> properties = Map.of("micronaut.server.port", -1);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST)) {
            assertThat(server.isRunning()).isTrue();
            assertThat(server.getPort()).isPositive();
        }
    }
}
