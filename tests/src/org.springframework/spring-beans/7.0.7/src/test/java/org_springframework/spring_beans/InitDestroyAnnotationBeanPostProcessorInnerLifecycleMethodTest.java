/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InitDestroyAnnotationBeanPostProcessorInnerLifecycleMethodTest {
    @Test
    void invokesCustomAnnotatedLifecycleMethods() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        InitDestroyAnnotationBeanPostProcessor processor = new InitDestroyAnnotationBeanPostProcessor();
        processor.setInitAnnotationType(Start.class);
        processor.setDestroyAnnotationType(Stop.class);
        factory.addBeanPostProcessor(processor);
        factory.registerBeanDefinition("resource", new RootBeanDefinition(Resource.class));
        Resource resource = factory.getBean("resource", Resource.class);
        assertEquals(List.of("started"), resource.events);
        factory.destroySingletons();
        assertEquals(List.of("started", "stopped"), resource.events);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Start {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Stop {
    }

    public static class Resource {
        private final List<String> events = new ArrayList<>();

        @Start
        private void start() {
            this.events.add("started");
        }

        @Stop
        private void stop() {
            this.events.add("stopped");
        }
    }
}
