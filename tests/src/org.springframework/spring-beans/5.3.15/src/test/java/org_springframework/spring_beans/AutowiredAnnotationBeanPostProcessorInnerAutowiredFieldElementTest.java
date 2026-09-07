/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class AutowiredAnnotationBeanPostProcessorInnerAutowiredFieldElementTest {
    @Test
    void injectsAutowiredField() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        Dependency dependency = new Dependency("field");
        factory.registerSingleton("dependency", dependency);
        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();
        processor.setBeanFactory(factory);
        FieldTarget target = new FieldTarget();

        processor.processInjection(target);

        assertThat(target.getDependency()).isSameAs(dependency);
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

    public static class FieldTarget {
        @Autowired
        private Dependency dependency;

        public Dependency getDependency() {
            return this.dependency;
        }
    }
}
