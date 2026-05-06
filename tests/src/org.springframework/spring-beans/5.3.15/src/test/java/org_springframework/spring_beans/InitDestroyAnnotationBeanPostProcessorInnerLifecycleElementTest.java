/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;

public class InitDestroyAnnotationBeanPostProcessorInnerLifecycleElementTest {

    @Test
    public void invokesConfiguredLifecycleMethods() {
        InitDestroyAnnotationBeanPostProcessor processor = new InitDestroyAnnotationBeanPostProcessor();
        processor.setInitAnnotationType(TestInit.class);
        processor.setDestroyAnnotationType(TestDestroy.class);
        LifecycleTarget target = new LifecycleTarget();

        processor.postProcessBeforeInitialization(target, "lifecycleTarget");
        assertThat(target.isInitialized()).isTrue();
        assertThat(target.isDestroyed()).isFalse();

        assertThat(processor.requiresDestruction(target)).isTrue();
        processor.postProcessBeforeDestruction(target, "lifecycleTarget");
        assertThat(target.isDestroyed()).isTrue();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface TestInit {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface TestDestroy {
    }

    public static class LifecycleTarget {

        private boolean initialized;

        private boolean destroyed;

        @TestInit
        private void initialize() {
            this.initialized = true;
        }

        @TestDestroy
        private void destroy() {
            this.destroyed = true;
        }

        public boolean isInitialized() {
            return this.initialized;
        }

        public boolean isDestroyed() {
            return this.destroyed;
        }
    }
}
