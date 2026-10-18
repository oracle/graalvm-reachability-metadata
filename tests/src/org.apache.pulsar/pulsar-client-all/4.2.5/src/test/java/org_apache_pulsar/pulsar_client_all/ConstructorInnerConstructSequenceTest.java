/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.api.Test;

public class ConstructorInnerConstructSequenceTest {

    @Test
    void constructsAnImmutableValueFromASequence() {
        final Endpoint endpoint = new Yaml().loadAs("[broker, 6650]", Endpoint.class);

        assertThat(endpoint.getHost()).isEqualTo("broker");
        assertThat(endpoint.getPort()).isEqualTo(6650);
    }

    public static final class Endpoint {
        private final String host;
        private final int port;

        public Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }
    }
}
