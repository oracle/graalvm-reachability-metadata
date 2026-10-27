/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tomcat.websocket.server.DefaultServerEndpointConfigurator;
import org.junit.jupiter.api.Test;

public class DefaultServerEndpointConfiguratorTest {
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    @Test
    void getEndpointInstanceCreatesEndpointWithPublicNoArgConstructor() throws InstantiationException {
        CONSTRUCTIONS.set(0);
        DefaultServerEndpointConfigurator configurator = new DefaultServerEndpointConfigurator();

        CountingEndpoint endpoint = configurator.getEndpointInstance(CountingEndpoint.class);

        assertThat(endpoint).isInstanceOf(CountingEndpoint.class);
        assertThat(CONSTRUCTIONS).hasValue(1);
    }

    public static class CountingEndpoint {
        public CountingEndpoint() {
            CONSTRUCTIONS.incrementAndGet();
        }
    }
}
