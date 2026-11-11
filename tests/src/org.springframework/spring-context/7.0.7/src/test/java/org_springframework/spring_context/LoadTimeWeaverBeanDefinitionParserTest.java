/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ByteArrayResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class LoadTimeWeaverBeanDefinitionParserTest {
    @Test
    void registersWeaverWithoutEnablingAbsentAspectConfiguration() {
        String xml = """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:context="http://www.springframework.org/schema/context"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans
                         https://www.springframework.org/schema/beans/spring-beans.xsd
                         http://www.springframework.org/schema/context
                         https://www.springframework.org/schema/context/spring-context.xsd">
                    <context:load-time-weaver/>
                </beans>
                """;
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
        reader.loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
        BeanDefinition definition = factory.getBeanDefinition("loadTimeWeaver");
        assertEquals("org.springframework.context.weaving.DefaultContextLoadTimeWeaver", definition.getBeanClassName());
        assertEquals(BeanDefinition.ROLE_INFRASTRUCTURE, definition.getRole());
        assertFalse(factory.containsBeanDefinition("org.springframework.context.config.internalAspectJWeavingEnabler"));
    }
}
