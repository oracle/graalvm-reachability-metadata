/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationProxyBuilderTest {

    @Test
    void createsConstraintAnnotationFromXmlMapping() {
        try (ValidatorFactory factory = ValidationTestSupport.xmlPatternFactory(MappedBean.class)) {
            Pattern pattern = (Pattern) factory.getValidator().validate(new MappedBean("123"))
                    .iterator().next().getConstraintDescriptor().getAnnotation();

            assertThat(pattern.regexp()).isEqualTo("[a-z]+");
            assertThat(pattern.flags()).containsExactly(Pattern.Flag.CASE_INSENSITIVE);
        }
    }

    public static class MappedBean {
        private final String value;

        public MappedBean(String value) {
            this.value = value;
        }
    }
}
