/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.SimpleInstantiationStrategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InitDestroyAnnotationBeanPostProcessorInnerLifecycleElementTest {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Start {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Stop {
    }

    public static class Worker {
        private boolean started;
        private boolean stopped;

        @Start
        public void begin() {
            started = true;
        }

        @Stop
        public void end() {
            stopped = true;
        }
    }

    @Test
    void invokesAnnotatedLifecycleMethods() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.setInstantiationStrategy(new SimpleInstantiationStrategy());
        InitDestroyAnnotationBeanPostProcessor processor = new InitDestroyAnnotationBeanPostProcessor();
        processor.setInitAnnotationType(Start.class);
        processor.setDestroyAnnotationType(Stop.class);
        factory.addBeanPostProcessor(processor);
        factory.registerBeanDefinition("worker", new RootBeanDefinition(Worker.class));
        Worker worker;
        try {
            worker = (Worker) factory.getBean("worker");
            assertTrue(worker.started);
            assertFalse(worker.stopped);
        } finally {
            factory.destroySingletons();
        }
        assertTrue(worker.stopped);
    }
}
