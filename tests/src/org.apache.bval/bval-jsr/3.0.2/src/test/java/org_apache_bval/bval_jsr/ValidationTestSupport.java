/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.bval.jsr.ApacheValidationProvider;
import org.apache.bval.jsr.ApacheValidatorConfiguration;

public final class ValidationTestSupport {

    private ValidationTestSupport() {
    }

    public static ValidatorFactory factory() {
        return Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory();
    }

    public static ValidatorFactory xmlPatternFactory(Class<?> beanType) {
        String mapping = """
                <constraint-mappings xmlns="https://jakarta.ee/xml/ns/validation/mapping" version="3.0">
                  <bean class="%s" ignore-annotations="true">
                    <field name="value">
                      <constraint annotation="jakarta.validation.constraints.Pattern">
                        <message>must match letters</message>
                        <element name="regexp">[a-z]+</element>
                        <element name="flags"><value>CASE_INSENSITIVE</value></element>
                      </constraint>
                    </field>
                  </bean>
                </constraint-mappings>
                """.formatted(beanType.getName());
        ApacheValidatorConfiguration configuration = Validation.byProvider(ApacheValidationProvider.class)
                .configure();
        configuration.addMapping(new ByteArrayInputStream(mapping.getBytes(StandardCharsets.UTF_8)));
        return configuration.buildValidatorFactory();
    }
}
