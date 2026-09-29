/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import java.lang.annotation.Annotation;

import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.BeanDescriptor;
import org.apache.bval.util.reflection.Reflection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionTest {

    @Test
    void discoversFieldsMethodsAndConstructorsWhileBuildingMetadata() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            BeanDescriptor descriptor = factory.getValidator().getConstraintsForClass(Profile.class);

            assertThat(descriptor.getConstraintsForProperty("name")).isNotNull();
            assertThat(descriptor.getConstraintsForMethod("rename", String.class)).isNotNull();
            assertThat(descriptor.getConstraintsForConstructor(String.class)).isNotNull();
        }
    }

    @Test
    void readsConstraintAnnotationAttribute() throws Exception {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            Annotation annotation = factory.getValidator().getConstraintsForClass(Profile.class)
                    .getConstraintsForProperty("name").getConstraintDescriptors().iterator().next().getAnnotation();

            assertThat(Reflection.getAnnotationValue(annotation, "message")).isEqualTo("required");
            assertThat(Reflection.getAnnotationValue(annotation, "unknown")).isNull();
        }
    }

    public static class Profile {

        @NotBlank(message = "required")
        private String name;

        public Profile(@Size(min = 2) String name) {
            this.name = name;
        }

        @NotBlank
        public String getName() {
            return name;
        }

        public void rename(@NotBlank String replacement) {
            name = replacement;
        }
    }
}
