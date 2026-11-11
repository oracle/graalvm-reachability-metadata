/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigurationClassBeanDefinitionReaderTest {
    @Test
    void importsXmlBeansAlongsideAnnotationConfiguration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Imported.class)) {
            assertEquals("warehouse", context.getBean("importedName"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportResource("classpath:org_springframework/spring_context/imported.xml")
    public static class Imported {
    }
}
