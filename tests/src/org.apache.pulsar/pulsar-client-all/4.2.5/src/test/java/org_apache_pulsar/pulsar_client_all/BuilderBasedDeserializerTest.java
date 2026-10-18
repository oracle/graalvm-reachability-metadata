/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

public class BuilderBasedDeserializerTest {

    @Test
    void finishesObjectDeserializationThroughABuilder() throws Exception {
        final ClientSettings settings = new ObjectMapper().readValue(
                "{\"serviceUrl\":\"pulsar://broker:6650\",\"connections\":4}", ClientSettings.class);

        assertThat(settings.serviceUrl).isEqualTo("pulsar://broker:6650");
        assertThat(settings.connections).isEqualTo(4);
    }

    @JsonDeserialize(builder = ClientSettings.Builder.class)
    public static final class ClientSettings {
        private final String serviceUrl;
        private final int connections;

        private ClientSettings(String serviceUrl, int connections) {
            this.serviceUrl = serviceUrl;
            this.connections = connections;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static final class Builder {
            private String serviceUrl;
            private int connections;

            public Builder serviceUrl(String serviceUrl) {
                this.serviceUrl = serviceUrl;
                return this;
            }

            public Builder connections(int connections) {
                this.connections = connections;
                return this;
            }

            public ClientSettings build() {
                return new ClientSettings(serviceUrl, connections);
            }
        }
    }
}
