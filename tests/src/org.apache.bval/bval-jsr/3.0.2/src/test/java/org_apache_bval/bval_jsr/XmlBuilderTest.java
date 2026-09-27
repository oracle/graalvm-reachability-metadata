/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.apache.bval.jsr.ApacheValidationProvider;
import org.junit.jupiter.api.Test;

import static jakarta.validation.Validation.byProvider;
import static org.assertj.core.api.Assertions.assertThat;

public class XmlBuilderTest {

    @Test
    void validatesConstraintWithArrayValuesLoadedFromXml() {
        String mapping = """
                <?xml version="1.0" encoding="UTF-8"?>
                <constraint-mappings xmlns="https://jakarta.ee/xml/ns/validation/mapping" version="3.0">
                    <bean class="org_apache_bval.bval_jsr.XmlBuilderTest$Account">
                        <field name="code">
                            <constraint annotation="jakarta.validation.constraints.Pattern">
                                <message>code must be alpha</message>
                                <element name="regexp">ALPHA</element>
                                <element name="flags">
                                    <value>CASE_INSENSITIVE</value>
                                    <value>UNICODE_CASE</value>
                                </element>
                            </constraint>
                        </field>
                    </bean>
                </constraint-mappings>
                """;

        ByteArrayInputStream input = new ByteArrayInputStream(mapping.getBytes(StandardCharsets.UTF_8));
        try (ValidatorFactory factory = byProvider(ApacheValidationProvider.class).configure().addMapping(input)
                .buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertThat(validator.validate(new Account("alpha"))).isEmpty();
            assertThat(validator.validate(new Account("beta"))).singleElement()
                    .satisfies(violation -> {
                        assertThat(violation.getPropertyPath()).hasToString("code");
                        assertThat(violation.getMessage()).isEqualTo("code must be alpha");
                    });
        }
    }

    public static final class Account {
        private final String code;

        public Account(String code) {
            this.code = code;
        }
    }
}
