/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ByteArrayResource;

import static org.junit.jupiter.api.Assertions.*;

public class XmlBeanDefinitionReaderTest {
    @Test
    void loadsValidatedBeanDefinitionAndResolvesPropertyValues() {
        String xml = """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans
                       http://www.springframework.org/schema/beans/spring-beans.xsd">
                    <bean id="person" class="org_springframework.spring.TestBeans$Person">
                        <property name="name" value="XML configured"/>
                        <property name="age" value="35"/>
                    </bean>
                </beans>
                """;
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
        assertEquals(1, reader.loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8))));
        try {
            TestBeans.Person person = (TestBeans.Person) factory.getBean("person");
            assertEquals("XML configured", person.getName());
            assertEquals(35, person.getAge());
        } finally {
            factory.destroySingletons();
        }
    }
}
