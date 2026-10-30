/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_commons;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.cloud.client.discovery.ManagementServerPortUtils;
import org.springframework.cloud.client.discovery.ManagementServerPortUtils.ManagementServerPort;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagementServerPortUtilsTest {

    @Test
    void returnsConfiguredManagementPort() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ManagementServerProperties management = new ManagementServerProperties();
        management.setPort(9090);
        beanFactory.registerSingleton("managementServerProperties", management);

        assertThat(ManagementServerPortUtils.getPort(beanFactory)).isEqualTo(9090);
    }

    @Test
    void returnsNoPortWhenManagementPropertiesAreAbsent() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        assertThat(ManagementServerPortUtils.getPort(beanFactory)).isNull();
    }

    @Test
    void identifiesDisabledManagementServer() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ManagementServerProperties management = new ManagementServerProperties();
        management.setPort(-1);
        beanFactory.registerSingleton("managementServerProperties", management);

        assertThat(ManagementServerPortUtils.getPort(beanFactory)).isEqualTo(-1);
        assertThat(ManagementServerPortUtils.get(beanFactory)).isEqualTo(ManagementServerPort.DISABLE);
        assertThat(ManagementServerPortUtils.isDisabled(beanFactory)).isTrue();
        assertThat(ManagementServerPortUtils.isDifferent(beanFactory)).isFalse();
        assertThat(ManagementServerPortUtils.isSame(beanFactory)).isFalse();
    }

    @Test
    void identifiesSeparateManagementServerInNonWebContext() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ServerProperties server = new ServerProperties();
        server.setPort(8080);
        ManagementServerProperties management = new ManagementServerProperties();
        management.setPort(9090);
        beanFactory.registerSingleton("serverProperties", server);
        beanFactory.registerSingleton("managementServerProperties", management);

        assertThat(ManagementServerPortUtils.get(beanFactory)).isEqualTo(ManagementServerPort.DIFFERENT);
        assertThat(ManagementServerPortUtils.isDifferent(beanFactory)).isTrue();
        assertThat(ManagementServerPortUtils.isDisabled(beanFactory)).isFalse();
        assertThat(ManagementServerPortUtils.isSame(beanFactory)).isFalse();
    }
}
