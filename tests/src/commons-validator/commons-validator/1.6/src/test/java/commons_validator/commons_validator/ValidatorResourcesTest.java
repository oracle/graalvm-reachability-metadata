/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_validator.commons_validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.commons.validator.Field;
import org.apache.commons.validator.Form;
import org.apache.commons.validator.ValidatorResources;
import org.junit.jupiter.api.Test;

public class ValidatorResourcesTest {
    private static final String FORM_NAME = "customerForm";
    private static final String FIELD_NAME = "emailAddress";

    @Test
    void parsesValidationXmlFromInputStreamUsingPackagedDigesterRulesAndDtds() throws Exception {
        try (InputStream validationXml = validationXml()) {
            ValidatorResources resources = new ValidatorResources(validationXml);

            Form form = resources.getForm(Locale.US, FORM_NAME);

            assertThat(resources.getValidatorActions()).isEmpty();
            assertThat(form).isNotNull();
            Field field = form.getField(FIELD_NAME);
            assertThat(field).isNotNull();
            assertThat(field.getProperty()).isEqualTo(FIELD_NAME);
        }
    }

    private static InputStream validationXml() {
        String xml = """
                <!DOCTYPE form-validation PUBLIC
                 "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.4.0//EN"
                 "http://commons.apache.org/dtds/validator_1_4_0.dtd">
                <form-validation>
                  <formset>
                    <form name="customerForm">
                      <field property="emailAddress"/>
                    </form>
                  </formset>
                </form-validation>
                """;
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
