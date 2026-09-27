/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlBuilderTest {

    @Test
    void loadsMappedTypesAndConvertsArrayElements() {
        try (ValidatorFactory factory = ValidationTestSupport.xmlPatternFactory(MappedBean.class)) {
            assertThat(factory.getValidator().validate(new MappedBean("123"))).singleElement()
                    .satisfies(violation -> assertThat(violation.getMessage()).isEqualTo("must match letters"));
            assertThat(factory.getValidator().validate(new MappedBean("ABC"))).isEmpty();
        }
    }

    public static class MappedBean {
        private final String value;

        public MappedBean(String value) {
            this.value = value;
        }
    }
}
