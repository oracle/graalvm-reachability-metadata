/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.api.Test;

public class ConstructorInnerConstructScalarTest {

    @Test
    void constructsBeanPropertiesCollectionsEnumsAndStringValueObjects() {
        final String document = """
                name: broker-a
                state: ACTIVE
                endpoint: pulsar://localhost:6650
                ports: [6650, 6651]
                """;

        final Configuration configuration = new Yaml().loadAs(document, Configuration.class);
        final String dumped = new Yaml().dump(configuration);

        assertThat(configuration.getName()).isEqualTo("broker-a");
        assertThat(configuration.getState()).isEqualTo(State.ACTIVE);
        assertThat(configuration.getEndpoint().getValue()).isEqualTo("pulsar://localhost:6650");
        assertThat(configuration.getPorts()).containsExactly(6650, 6651);
        assertThat(dumped).contains("broker-a", "ACTIVE", "6650");
    }

    public enum State {
        ACTIVE,
        INACTIVE
    }

    public static final class Endpoint {
        private final String value;

        public Endpoint(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class Configuration {
        private String name;
        private State state;
        private Endpoint endpoint;
        private List<Integer> ports;

        public Configuration() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public State getState() {
            return state;
        }

        public void setState(State state) {
            this.state = state;
        }

        public Endpoint getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        public List<Integer> getPorts() {
            return ports;
        }

        public void setPorts(List<Integer> ports) {
            this.ports = ports;
        }
    }
}
