/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import java.lang.annotation.Annotation;

import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationProxyTest {

    @Test
    void generatedAnnotationImplementsAnnotationContract() {
        try (ValidatorFactory factory = ValidationTestSupport.xmlPatternFactory(MappedBean.class)) {
            Annotation annotation = factory.getValidator().validate(new MappedBean("123")).iterator().next()
                    .getConstraintDescriptor().getAnnotation();

            assertThat(annotation.annotationType().getName()).isEqualTo("jakarta.validation.constraints.Pattern");
            assertThat(annotation).isEqualTo(annotation).hasSameHashCodeAs(annotation);
            assertThat(annotation.toString()).contains("regexp=[a-z]+");
        }
    }

    public static class MappedBean {
        private final String value;

        public MappedBean(String value) {
            this.value = value;
        }
    }
}
