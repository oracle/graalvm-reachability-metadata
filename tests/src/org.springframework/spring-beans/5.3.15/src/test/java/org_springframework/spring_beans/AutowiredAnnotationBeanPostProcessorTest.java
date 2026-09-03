/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

public class AutowiredAnnotationBeanPostProcessorTest {
    @Test
    void discoversAutowiredConstructor() {
        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();

        Constructor<?>[] constructors = processor.determineCandidateConstructors(ConstructorBean.class, "bean");

        assertThat(constructors).hasSize(2);
        assertThat(constructors[0].getParameterTypes()).containsExactly(Dependency.class);
    }

    public static class ConstructorBean {
        public ConstructorBean() {
        }

        @Autowired(required = false)
        public ConstructorBean(Dependency dependency) {
        }
    }

    public static class Dependency {
    }
}
