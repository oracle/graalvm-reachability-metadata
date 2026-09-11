/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_integration.spring_integration_sftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ByteArrayResource;

public class SftpNamespaceHandlerTest {

    @Test
    void parsesOutboundChannelAdapterNamespaceConfiguration() {
        String configuration = """
                <beans xmlns="https://www.springframework.org/schema/beans"
                       xmlns:int="http://www.springframework.org/schema/integration"
                       xmlns:int-sftp="http://www.springframework.org/schema/integration/sftp">
                    <int:channel id="input"/>
                    <bean id="sessionFactory"
                          class="org.springframework.integration.sftp.session.DefaultSftpSessionFactory"/>
                    <int-sftp:outbound-channel-adapter id="sftpAdapter" channel="input"
                            session-factory="sessionFactory" remote-directory="outbound"/>
                </beans>
                """;
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(beanFactory);
        reader.setValidationMode(XmlBeanDefinitionReader.VALIDATION_NONE);

        reader.loadBeanDefinitions(new ByteArrayResource(configuration.getBytes(StandardCharsets.UTF_8)));

        assertThat(beanFactory.containsBeanDefinition("input")).isTrue();
        assertThat(beanFactory.containsBeanDefinition("sessionFactory")).isTrue();
        assertThat(beanFactory.containsBeanDefinition("sftpAdapter")).isTrue();
    }
}
