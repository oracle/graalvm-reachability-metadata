/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.pulsar.shade.org.yaml.snakeyaml.LoaderOptions;
import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.apache.pulsar.shade.org.yaml.snakeyaml.constructor.Constructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ConstructorInnerConstructScalarTest {
    @Test
    void constructsBeanScalarsSequencesAndProperties() {
        Constructor constructor = new Constructor(BrokerConfig.class, new LoaderOptions());
        Yaml yaml = new Yaml(constructor);

        BrokerConfig config = yaml.load("""
                name: local
                port: 6650
                address: [localhost, 6650]
                roles: [producer, consumer]
                """);
        String emitted = yaml.dumpAsMap(config);

        assertThat(config.getName()).isEqualTo("local");
        assertThat(config.getPort().value()).isEqualTo(6650);
        assertThat(config.getAddress().host()).isEqualTo("localhost");
        assertThat(config.getAddress().port()).isEqualTo(6650);
        assertThat(config.getRoles()).containsExactly("producer", "consumer");
        assertThat(emitted).contains("name: local", "producer", "consumer");
    }

    public static final class BrokerConfig {
        private String name;
        private Port port;
        private Address address;
        private List<String> roles;

        public BrokerConfig() { }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Port getPort() {
            return port;
        }

        public void setPort(Port port) {
            this.port = port;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }

    public static final class Port {
        private final int value;

        public Port(String value) {
            this.value = Integer.parseInt(value);
        }

        public int getValue() {
            return value;
        }

        public int value() {
            return value;
        }
    }

    public static final class Address {
        private final String host;
        private final int port;

        public Address(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public String host() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public int port() {
            return port;
        }
    }
}
