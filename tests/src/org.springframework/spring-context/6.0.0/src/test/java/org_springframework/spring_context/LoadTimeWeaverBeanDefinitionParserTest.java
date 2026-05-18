/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.weaving.AspectJWeavingEnabler;
import org.springframework.core.io.ByteArrayResource;

public class LoadTimeWeaverBeanDefinitionParserTest {

    @Test
    void autodetectAspectJWeavingChecksForAopXmlResource() {
        RecordingClassLoader classLoader = new RecordingClassLoader(getClass().getClassLoader());
        GenericApplicationContext context = new GenericApplicationContext();
        context.setClassLoader(classLoader);
        context.getDefaultListableBeanFactory().setBeanClassLoader(classLoader);

        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(context);
        reader.setBeanClassLoader(classLoader);
        reader.setResourceLoader(context);
        reader.setValidating(false);

        int beanDefinitionCount = reader.loadBeanDefinitions(new ByteArrayResource(loadTimeWeaverXml(),
                "load-time-weaver context XML"));

        assertEquals(1, beanDefinitionCount);
        assertTrue(context.containsBeanDefinition(ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME));
        assertEquals(1, classLoader.getAspectJAopXmlResourceLookups());
    }

    private static byte[] loadTimeWeaverXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:context="http://www.springframework.org/schema/context">
                    <context:load-time-weaver/>
                </beans>
                """.getBytes(StandardCharsets.UTF_8);
    }

    private static final class RecordingClassLoader extends ClassLoader {

        private final AtomicInteger aspectJAopXmlResourceLookups = new AtomicInteger();

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            if (AspectJWeavingEnabler.ASPECTJ_AOP_XML_RESOURCE.equals(name)) {
                this.aspectJAopXmlResourceLookups.incrementAndGet();
                return null;
            }
            return super.getResource(name);
        }

        private int getAspectJAopXmlResourceLookups() {
            return this.aspectJAopXmlResourceLookups.get();
        }
    }
}
