/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaBeanBinderInnerBeanPropertyTest {

    @Test
    void updatesAnExistingNestedBeanThroughItsGetterAndSetter() {
        Client client = new Client();
        Connection original = client.getConnection();
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "client.connection.host", "database.example", "client.connection.port", "5432")));

        Client result = binder.bind("client", Bindable.ofInstance(client)).get();

        assertThat(result).isSameAs(client);
        assertThat(result.getConnection()).isSameAs(original);
        assertThat(original.getHost()).isEqualTo("database.example");
        assertThat(original.getPort()).isEqualTo(5432);
    }

    public static class Client {
        private final Connection connection = new Connection();

        public Connection getConnection() {
            return connection;
        }
    }

    public static class Connection {
        private String host = "localhost";
        private int port;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}
