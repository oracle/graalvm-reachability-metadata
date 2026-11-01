/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ByteArrayResource;

import static org.junit.jupiter.api.Assertions.*;

public class UtilNamespaceHandlerTest {
    @Test
    void parsesUtilityCollectionsAndConstantBeans() {
        String xml = """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:util="http://www.springframework.org/schema/util"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans
                       http://www.springframework.org/schema/beans/spring-beans.xsd
                       http://www.springframework.org/schema/util
                       http://www.springframework.org/schema/util/spring-util.xsd">
                    <util:list id="names"><value>Ada</value><value>Grace</value></util:list>
                    <util:map id="counts"><entry key="one" value="1"/></util:map>
                    <util:constant id="category" static-field="org_springframework.spring.TestBeans$Fields.CATEGORY"/>
                </beans>
                """;
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(factory).loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
        try {
            assertEquals(List.of("Ada", "Grace"), factory.getBean("names"));
            assertEquals(Map.of("one", "1"), factory.getBean("counts"));
            assertEquals("sample", factory.getBean("category"));
        } finally {
            factory.destroySingletons();
        }
    }
}
