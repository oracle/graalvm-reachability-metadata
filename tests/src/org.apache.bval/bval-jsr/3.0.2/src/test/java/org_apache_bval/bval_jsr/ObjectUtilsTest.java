/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectUtilsTest {

    @Test
    void rewritesDefaultGroupForConstraintInheritedFromSuperclass() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            assertThat(factory.getValidator().validate(new Child(""))).singleElement()
                    .satisfies(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("name"));
        }
    }

    public static class Parent {
        @NotBlank
        private final String name;

        public Parent(String name) {
            this.name = name;
        }
    }

    public static class Child extends Parent {
        public Child(String name) {
            super(name);
        }
    }
}
