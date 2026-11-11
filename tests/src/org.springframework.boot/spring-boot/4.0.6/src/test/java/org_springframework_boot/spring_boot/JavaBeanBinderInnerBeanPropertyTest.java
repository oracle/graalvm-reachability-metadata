/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.ArrayList;
import java.util.List;
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

    @Test
    void readsExistingCollectionAndInvokesScalarSetter() {
        Client client = new Client();
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "client.name", "warehouse", "client.tags[0]", "fast", "client.tags[1]", "local")));
        Client bound = binder.bind("client", Bindable.ofInstance(client)).get();

        assertThat(bound).isSameAs(client);
        assertThat(client.getName()).isEqualTo("warehouse");
        assertThat(client.getTags()).containsExactly("fast", "local");
    }

    public static class Client {
        private String name;
        private final List<String> tags = new ArrayList<>();
        private final Connection connection = new Connection();

        public Connection getConnection() {
            return this.connection;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getTags() {
            return this.tags;
        }
    }

    public static class Connection {
        private String host = "localhost";
        private int port;

        public String getHost() {
            return this.host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return this.port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}
