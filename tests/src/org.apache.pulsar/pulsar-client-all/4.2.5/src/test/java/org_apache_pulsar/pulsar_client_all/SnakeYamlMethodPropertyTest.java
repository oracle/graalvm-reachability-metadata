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

public class SnakeYamlMethodPropertyTest {
    @Test
    void readsAndWritesBeanPropertiesThroughAccessors() {
        final Yaml yaml = new Yaml();
        final Configuration configuration = yaml.loadAs("serviceUrl: pulsar://broker:6650", Configuration.class);

        assertThat(configuration.getServiceUrl()).isEqualTo("pulsar://broker:6650");
        assertThat(yaml.dumpAsMap(configuration)).contains("serviceUrl: pulsar://broker:6650");
    }

    public static final class Configuration {
        private String serviceUrl;

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String configuredUrl) {
            serviceUrl = configuredUrl;
        }
    }
}
