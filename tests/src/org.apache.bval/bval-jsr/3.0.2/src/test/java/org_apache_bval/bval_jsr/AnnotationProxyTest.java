/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import java.io.ByteArrayInputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import org.apache.bval.jsr.ApacheValidationProvider;
import org.junit.jupiter.api.Test;

import static jakarta.validation.Validation.byProvider;
import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationProxyTest {

    @Test
    void xmlConstraintAnnotationEqualsEquivalentDeclaredAnnotation() {
        String mapping = """
                <?xml version="1.0" encoding="UTF-8"?>
                <constraint-mappings xmlns="https://jakarta.ee/xml/ns/validation/mapping" version="3.0">
                    <bean class="org_apache_bval.bval_jsr.AnnotationProxyTest$XmlMappedName">
                        <field name="value">
                            <constraint annotation="jakarta.validation.constraints.Size">
                                <element name="min">2</element>
                                <element name="max">4</element>
                            </constraint>
                        </field>
                    </bean>
                </constraint-mappings>
                """;

        ByteArrayInputStream input = new ByteArrayInputStream(mapping.getBytes(StandardCharsets.UTF_8));
        try (ValidatorFactory factory = byProvider(ApacheValidationProvider.class).configure().addMapping(input)
                .buildValidatorFactory()) {
            Validator validator = factory.getValidator();
            Annotation xmlAnnotation = constraintAnnotation(validator, XmlMappedName.class);
            Annotation declaredAnnotation = constraintAnnotation(validator, DeclaredName.class);

            assertThat(xmlAnnotation.equals(declaredAnnotation)).isTrue();
            assertThat(xmlAnnotation.hashCode()).isEqualTo(declaredAnnotation.hashCode());
            assertThat(validator.validate(new XmlMappedName("x"))).singleElement();
            assertThat(validator.validate(new XmlMappedName("good"))).isEmpty();
        }
    }

    private static Annotation constraintAnnotation(Validator validator, Class<?> beanType) {
        return validator.getConstraintsForClass(beanType).getConstraintsForProperty("value")
                .getConstraintDescriptors().iterator().next().getAnnotation();
    }

    public static final class XmlMappedName {
        private final String value;

        public XmlMappedName(String value) {
            this.value = value;
        }
    }

    public static final class DeclaredName {
        @Size(min = 2, max = 4)
        private final String value = "good";
    }
}
