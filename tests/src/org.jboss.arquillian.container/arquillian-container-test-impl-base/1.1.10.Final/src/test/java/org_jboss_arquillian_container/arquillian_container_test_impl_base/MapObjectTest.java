/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_impl_base;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.arquillian.container.test.impl.MapObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapObjectTest {
    @Test
    void populateDiscoversSettersAndInvokesMatchingProperties() throws Exception {
        ContainerConfiguration configuration = new ContainerConfiguration();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "  managed   container\n");
        values.put("port", "8181");
        values.put("requestTimeout", "120000");
        values.put("loadFactor", "0.75");
        values.put("enabled", "true");

        MapObject.populate(configuration, values);

        assertThat(configuration.getName()).isEqualTo("managed container");
        assertThat(configuration.getPort()).isEqualTo(8181);
        assertThat(configuration.getRequestTimeout()).isEqualTo(120000L);
        assertThat(configuration.getLoadFactor()).isEqualTo(0.75D);
        assertThat(configuration.isEnabled()).isTrue();
    }

    public static final class ContainerConfiguration {
        private String name;
        private int port;
        private long requestTimeout;
        private double loadFactor;
        private boolean enabled;

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getPort() {
            return port;
        }

        public void setRequestTimeout(long requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        public long getRequestTimeout() {
            return requestTimeout;
        }

        public void setLoadFactor(double loadFactor) {
            this.loadFactor = loadFactor;
        }

        public double getLoadFactor() {
            return loadFactor;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
