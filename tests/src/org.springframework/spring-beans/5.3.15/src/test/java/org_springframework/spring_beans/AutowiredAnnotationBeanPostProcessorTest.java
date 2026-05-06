/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;

public class AutowiredAnnotationBeanPostProcessorTest {

    @Test
    public void determinesCandidateConstructorsFromAutowiredUserClassConstructor() {
        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();

        Constructor<?>[] constructors = processor.determineCandidateConstructors(
                ProxiedBean$$Generated.class, "proxiedBean");

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getDeclaringClass()).isEqualTo(ProxiedBean$$Generated.class);
        assertThat(constructors[0].getParameterCount()).isEqualTo(1);
    }

    public static class ProxiedBean {
        private final String value;

        @Autowired
        public ProxiedBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    // CheckStyle: start generated
    public static class ProxiedBean$$Generated extends ProxiedBean {

        public ProxiedBean$$Generated(String value) {
            super(value);
        }
    }
    // CheckStyle: stop generated
}
