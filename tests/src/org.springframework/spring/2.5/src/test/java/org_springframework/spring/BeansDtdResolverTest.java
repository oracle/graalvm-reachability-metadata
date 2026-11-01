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

public class BeansDtdResolverTest {
    @Test
    void resolvesPackagedDtdWhileLoadingBeanDefinitions() {
        String xml = """
                <!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN//EN" "http://www.springframework.org/dtd/spring-beans.dtd">
                <beans>
                    <bean id="person" class="org_springframework.spring.TestBeans$Person">
                        <property name="name"><value>DTD configured</value></property>
                    </bean>
                </beans>
                """;
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(factory).loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
        try {
            assertEquals("DTD configured", ((TestBeans.Person) factory.getBean("person")).getName());
        } finally {
            factory.destroySingletons();
        }
    }
}
