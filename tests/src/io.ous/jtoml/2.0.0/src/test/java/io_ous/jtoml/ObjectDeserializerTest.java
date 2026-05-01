/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ous.jtoml;

import io.ous.jtoml.JToml;
import io.ous.jtoml.Toml;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectDeserializerTest {
    @Test
    void tomlTableDeserializesPrivateInheritedAndNestedFields() {
        Toml toml = JToml.parseString("""
                port = 8443
                name = "inventory-service"
                enabled = true
                ratio = 0.75
                mode = "ACTIVE"
                tags = ["blue", "canary"]

                [database]
                host = "db.internal"
                timeout = 30
                """);

        ServiceConfig serviceConfig = toml.asObject(ServiceConfig.class);

        assertThat(serviceConfig.getPort()).isEqualTo(8443L);
        assertThat(serviceConfig.getName()).isEqualTo("inventory-service");
        assertThat(serviceConfig.getEnabled()).isTrue();
        assertThat(serviceConfig.getRatio()).isEqualTo(0.75D);
        assertThat(serviceConfig.getMode()).isEqualTo(Mode.ACTIVE);
        assertThat(serviceConfig.getTags()).containsExactly("blue", "canary");
        assertThat(serviceConfig.getDatabase().getHost()).isEqualTo("db.internal");
        assertThat(serviceConfig.getDatabase().getTimeout()).isEqualTo(30L);
    }

    public enum Mode {
        ACTIVE,
        PASSIVE
    }

    public static class BaseConfig {
        private Long port;

        public BaseConfig() {
        }

        public Long getPort() {
            return port;
        }
    }

    public static class ServiceConfig extends BaseConfig {
        private String name;
        private Boolean enabled;
        private Double ratio;
        private Mode mode;
        private List<String> tags;
        private DatabaseConfig database;

        public ServiceConfig() {
        }

        public String getName() {
            return name;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public Double getRatio() {
            return ratio;
        }

        public Mode getMode() {
            return mode;
        }

        public List<String> getTags() {
            return tags;
        }

        public DatabaseConfig getDatabase() {
            return database;
        }
    }

    public static class DatabaseConfig {
        private String host;
        private Long timeout;

        public DatabaseConfig() {
        }

        public String getHost() {
            return host;
        }

        public Long getTimeout() {
            return timeout;
        }
    }
}
