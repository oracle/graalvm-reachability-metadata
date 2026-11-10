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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

public class InitDestroyAnnotationBeanPostProcessorInnerLifecycleElementTest {
    @Test
    void invokesAnnotatedLifecycleMethods() {
        InitDestroyAnnotationBeanPostProcessor processor = new InitDestroyAnnotationBeanPostProcessor();
        processor.setInitAnnotationType(OnStart.class);
        processor.setDestroyAnnotationType(OnStop.class);
        LifecycleBean bean = new LifecycleBean();

        processor.postProcessBeforeInitialization(bean, "lifecycleBean");
        processor.postProcessBeforeDestruction(bean, "lifecycleBean");

        assertThat(bean.isStarted()).isTrue();
        assertThat(bean.isStopped()).isTrue();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface OnStart {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface OnStop {
    }

    public static class LifecycleBean {
        private boolean started;
        private boolean stopped;

        @OnStart
        private void start() {
            this.started = true;
        }

        @OnStop
        private void stop() {
            this.stopped = true;
        }

        public boolean isStarted() {
            return this.started;
        }

        public boolean isStopped() {
            return this.stopped;
        }
    }
}
