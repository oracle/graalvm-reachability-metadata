/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class AutowiredAnnotationBeanPostProcessorInnerAutowiredMethodElementTest {

    @Test
    public void injectsAutowiredMethodArguments() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        Dependency dependency = new Dependency("method dependency");
        beanFactory.registerSingleton("dependency", dependency);

        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();
        processor.setBeanFactory(beanFactory);

        MethodInjectionTarget target = new MethodInjectionTarget();
        processor.processInjection(target);

        assertThat(target.getDependency()).isSameAs(dependency);
        assertThat(target.getValue()).isEqualTo("method dependency");
        assertThat(target.getInvocationCount()).isEqualTo(1);
    }

    public static class MethodInjectionTarget {

        private Dependency dependency;

        private int invocationCount;

        @Autowired
        public void configure(Dependency dependency) {
            this.dependency = dependency;
            this.invocationCount++;
        }

        public Dependency getDependency() {
            return this.dependency;
        }

        public String getValue() {
            return this.dependency.getValue();
        }

        public int getInvocationCount() {
            return this.invocationCount;
        }
    }

    public static class Dependency {

        private final String value;

        public Dependency(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }
    }
}
