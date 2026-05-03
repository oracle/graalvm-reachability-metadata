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
    void mapsTomlValuesToPojoFields() {
        Toml toml = JToml.parseString("""
                title = "JToml"
                enabled = true
                requests = 42
                timeout = 2.5
                tags = ["native", "metadata"]
                mode = "WRITE"

                [database]
                host = "localhost"
                port = 5432
                """);

        ApplicationConfig config = toml.asObject(ApplicationConfig.class);

        assertThat(config.getTitle()).isEqualTo("JToml");
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getRequests()).isEqualTo(42L);
        assertThat(config.getTimeout()).isEqualTo(2.5D);
        assertThat(config.getTags()).containsExactly("native", "metadata");
        assertThat(config.getMode()).isEqualTo(Mode.WRITE);
        assertThat(config.getDatabase().getHost()).isEqualTo("localhost");
        assertThat(config.getDatabase().getPort()).isEqualTo(5432L);
    }

    public static class ApplicationConfig {
        private String title;
        private Boolean enabled;
        private Long requests;
        private Double timeout;
        private List<String> tags;
        private Mode mode;
        private DatabaseConfig database;

        public String getTitle() {
            return title;
        }

        public Boolean isEnabled() {
            return enabled;
        }

        public Long getRequests() {
            return requests;
        }

        public Double getTimeout() {
            return timeout;
        }

        public List<String> getTags() {
            return tags;
        }

        public Mode getMode() {
            return mode;
        }

        public DatabaseConfig getDatabase() {
            return database;
        }
    }

    public static class DatabaseConfig {
        private String host;
        private Long port;

        public String getHost() {
            return host;
        }

        public Long getPort() {
            return port;
        }
    }

    public enum Mode {
        READ,
        WRITE
    }
}
