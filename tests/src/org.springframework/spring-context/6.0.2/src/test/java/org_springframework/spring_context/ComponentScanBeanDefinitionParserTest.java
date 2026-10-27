/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ComponentScanBeanDefinitionParser;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.lang.Nullable;

public class ComponentScanBeanDefinitionParserTest {

    @Test
    void beanNameGeneratorAttributeInstantiatesUserDefinedStrategy() throws ParserConfigurationException {
        CountingBeanNameGenerator.reset();
        Element componentScanElement = componentScanElement(CountingBeanNameGenerator.class.getName());
        BeanDefinitionRegistry registry = new GenericApplicationContext();
        CapturingClassPathBeanDefinitionScanner scanner = new CapturingClassPathBeanDefinitionScanner(registry);

        new ExposedComponentScanBeanDefinitionParser().parseBeanNameGenerator(componentScanElement, scanner);

        BeanNameGenerator beanNameGenerator = scanner.assignedBeanNameGenerator();
        assertNotNull(beanNameGenerator);
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ScannedComponent.class);
        String generatedName = beanNameGenerator.generateBeanName(beanDefinition, registry);
        assertEquals(CountingBeanNameGenerator.GENERATED_NAME, generatedName);
        assertEquals(1, CountingBeanNameGenerator.constructorCalls());
    }

    private static Element componentScanElement(String beanNameGeneratorClassName) throws ParserConfigurationException {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element element = document.createElement("component-scan");
        element.setAttribute("name-generator", beanNameGeneratorClassName);
        return element;
    }

    public static class ExposedComponentScanBeanDefinitionParser extends ComponentScanBeanDefinitionParser {

        @Override
        public void parseBeanNameGenerator(Element element, ClassPathBeanDefinitionScanner scanner) {
            super.parseBeanNameGenerator(element, scanner);
        }
    }

    public static class CapturingClassPathBeanDefinitionScanner extends ClassPathBeanDefinitionScanner {

        private BeanNameGenerator beanNameGenerator;

        public CapturingClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry) {
            super(registry, false);
        }

        @Override
        public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
            super.setBeanNameGenerator(beanNameGenerator);
            this.beanNameGenerator = beanNameGenerator;
        }

        @Nullable
        BeanNameGenerator assignedBeanNameGenerator() {
            return this.beanNameGenerator;
        }
    }

    public static class CountingBeanNameGenerator implements BeanNameGenerator {

        static final String GENERATED_NAME = "generatedScannedComponentName";

        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public CountingBeanNameGenerator() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        @Override
        public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
            return GENERATED_NAME;
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
        }

        static int constructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }
    }

    public static class ScannedComponent {
    }
}
