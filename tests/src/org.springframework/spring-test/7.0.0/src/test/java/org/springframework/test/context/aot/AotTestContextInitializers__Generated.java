/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.springframework.test.context.aot;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import org_springframework.spring_test.TestBeanOverrideHandlerTest.TestBeanConfiguration;

public final class AotTestContextInitializers__Generated {
    private AotTestContextInitializers__Generated() {
    }

    public static Map<String, Supplier<ApplicationContextInitializer<ConfigurableApplicationContext>>> getContextInitializers() {
        return Map.of(
                "org_springframework.spring_test.TestBeanOverrideHandlerTest$TestBeanTestCase",
                TestBeanOverrideHandlerTest__ApplicationContextInitializer::new);
    }

    public static Map<String, Class<ApplicationContextInitializer<?>>> getContextInitializerClasses() {
        return Map.of(
                "org_springframework.spring_test.TestBeanOverrideHandlerTest$TestBeanTestCase",
                initializerClass(TestBeanOverrideHandlerTest__ApplicationContextInitializer.class));
    }

    static final class TestBeanOverrideHandlerTest__ApplicationContextInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            GenericApplicationContext context = (GenericApplicationContext) applicationContext;
            AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
            new AnnotatedBeanDefinitionReader(context).register(TestBeanConfiguration.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<ApplicationContextInitializer<?>> initializerClass(Class<?> initializerClass) {
        return (Class<ApplicationContextInitializer<?>>) initializerClass;
    }
}
