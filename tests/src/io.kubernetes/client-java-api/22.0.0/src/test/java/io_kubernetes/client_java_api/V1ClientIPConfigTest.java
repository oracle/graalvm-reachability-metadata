/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1ClientIPConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1ClientIPConfigTest {
    @Test
    void readsAndWritesAffinityTimeout() throws Exception {
        V1ClientIPConfig config = V1ClientIPConfig.fromJson(ModelJson.CLIENT_IP);
        assertThat(config.getTimeoutSeconds()).isEqualTo(600);
        ModelJson.assertJsonEquals(ModelJson.CLIENT_IP, config.toJson());
    }
}
