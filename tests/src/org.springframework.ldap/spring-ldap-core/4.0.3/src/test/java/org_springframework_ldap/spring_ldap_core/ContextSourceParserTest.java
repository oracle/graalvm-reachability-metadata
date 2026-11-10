/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.ServiceUnavailableException;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextSourceParserTest {
    @ParameterizedTest
    @ValueSource(strings = {"pooling", "pooling2"})
    void parsesConfiguredNonTransientExceptionClassesForBothPoolTypes(String pooling) {
        String xml = """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:ldap="http://www.springframework.org/schema/ldap">
                  <ldap:context-source id="directory" url="ldap://localhost:389"
                      username="cn=admin" password="secret" base="dc=example,dc=org" anonymous-read-only="true">
                    <ldap:%s test-on-borrow="true"
                        non-transient-exceptions="org.springframework.ldap.CommunicationException,org.springframework.ldap.ServiceUnavailableException"/>
                  </ldap:context-source>
                </beans>
                """.formatted(pooling);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(beans);
        reader.setValidationMode(XmlBeanDefinitionReader.VALIDATION_NONE);
        reader.setNamespaceAware(true);
        reader.loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
        BeanDefinition pool = beans.getBeanDefinition("directory");
        Object exceptions = pool.getPropertyValues().get("nonTransientExceptions");
        assertThat(exceptions).isEqualTo(Set.of(CommunicationException.class, ServiceUnavailableException.class));
        BeanDefinition target = (BeanDefinition) pool.getPropertyValues().get("contextSource");
        assertThat(target.getPropertyValues().get("base")).isEqualTo("dc=example,dc=org");
        BeanDefinition validator = (BeanDefinition) pool.getPropertyValues().get("dirContextValidator");
        assertThat(validator.getPropertyValues().get("filter")).isEqualTo("objectclass=*");
    }
}
