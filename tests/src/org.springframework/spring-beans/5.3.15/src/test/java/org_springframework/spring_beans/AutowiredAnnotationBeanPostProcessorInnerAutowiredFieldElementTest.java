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

public class AutowiredAnnotationBeanPostProcessorInnerAutowiredFieldElementTest {

    @Test
    public void injectsAutowiredFieldValue() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        Dependency dependency = new Dependency("field dependency");
        beanFactory.registerSingleton("dependency", dependency);

        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();
        processor.setBeanFactory(beanFactory);

        FieldInjectionTarget target = new FieldInjectionTarget();
        processor.processInjection(target);

        assertThat(target.getDependency()).isSameAs(dependency);
        assertThat(target.getDependency().getValue()).isEqualTo("field dependency");
    }

    public static class FieldInjectionTarget {

        @Autowired
        private Dependency dependency;

        public Dependency getDependency() {
            return this.dependency;
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
