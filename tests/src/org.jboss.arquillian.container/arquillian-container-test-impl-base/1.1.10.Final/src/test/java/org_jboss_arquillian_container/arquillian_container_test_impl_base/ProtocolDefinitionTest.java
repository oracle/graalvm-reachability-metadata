/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_impl_base;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.test.impl.domain.ProtocolDefinition;
import org.jboss.arquillian.container.test.spi.ContainerMethodExecutor;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager;
import org.jboss.arquillian.container.test.spi.client.protocol.Protocol;
import org.jboss.arquillian.container.test.spi.client.protocol.ProtocolConfiguration;
import org.jboss.arquillian.container.test.spi.command.CommandCallback;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtocolDefinitionTest {
    @Test
    void createProtocolConfigurationInstantiatesConfiguredProtocolConfigurationClass() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("host", "127.0.0.1");
        values.put("port", "8080");
        ProtocolDefinition definition = new ProtocolDefinition(new ConfigurableProtocol(), values, true);

        ProtocolConfiguration configuration = definition.createProtocolConfiguration();

        assertThat(configuration).isInstanceOf(ConfigurableProtocolConfiguration.class);
        ConfigurableProtocolConfiguration configurableConfiguration = (ConfigurableProtocolConfiguration) configuration;
        assertThat(configurableConfiguration.getHost()).isEqualTo("127.0.0.1");
        assertThat(configurableConfiguration.getPort()).isEqualTo(8080);
        assertThat(definition.getName()).isEqualTo("configurable");
        assertThat(definition.isDefaultProtocol()).isTrue();
    }

    public static final class ConfigurableProtocol implements Protocol<ConfigurableProtocolConfiguration> {
        @Override
        public Class<ConfigurableProtocolConfiguration> getProtocolConfigurationClass() {
            return ConfigurableProtocolConfiguration.class;
        }

        @Override
        public ProtocolDescription getDescription() {
            return new ProtocolDescription("configurable");
        }

        @Override
        public DeploymentPackager getPackager() {
            return null;
        }

        @Override
        public ContainerMethodExecutor getExecutor(ConfigurableProtocolConfiguration protocolConfiguration,
                ProtocolMetaData metaData, CommandCallback callback) {
            return null;
        }
    }

    public static final class ConfigurableProtocolConfiguration implements ProtocolConfiguration {
        private String host;
        private int port;

        public void setHost(String host) {
            this.host = host;
        }

        public String getHost() {
            return host;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getPort() {
            return port;
        }
    }
}
