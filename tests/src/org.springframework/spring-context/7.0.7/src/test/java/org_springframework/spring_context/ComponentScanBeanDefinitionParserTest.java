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
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org_springframework.spring_context.components.CatalogController;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComponentScanBeanDefinitionParserTest {
    @Test
    void instantiatesConfiguredNamingStrategyAndWiresScannedComponents() {
        String xml = """
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:context="http://www.springframework.org/schema/context"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans
                         https://www.springframework.org/schema/beans/spring-beans.xsd
                         http://www.springframework.org/schema/context
                         https://www.springframework.org/schema/context/spring-context.xsd">
                    <context:component-scan base-package="org_springframework.spring_context.components"
                        name-generator="%s"/>
                </beans>
                """.formatted(PrefixedNames.class.getName());
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            new XmlBeanDefinitionReader(context).loadBeanDefinitions(
                    new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
            context.refresh();
            CatalogController controller = context.getBean(
                    "scanned-" + CatalogController.class.getName(), CatalogController.class);
            assertEquals("catalog=north:books", controller.render());
        }
    }

    public static class PrefixedNames implements BeanNameGenerator {
        @Override
        public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
            return "scanned-" + definition.getBeanClassName();
        }
    }
}
