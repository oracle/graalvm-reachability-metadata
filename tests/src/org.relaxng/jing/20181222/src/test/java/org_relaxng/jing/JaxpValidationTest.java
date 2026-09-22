/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_relaxng.jing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thaiopensource.relaxng.jaxp.XMLSyntaxSchemaFactory;
import java.io.StringReader;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

public class JaxpValidationTest {
    private static final String INTERLEAVED_CATALOG_SCHEMA = """
            <element xmlns="http://relaxng.org/ns/structure/1.0" name="catalog">
              <interleave>
                <element name="title"><text/></element>
                <element name="updated"><text/></element>
              </interleave>
            </element>
            """;

    @Test
    void validatesRelaxNgThroughJaxpSchemaFactory() throws Exception {
        SchemaFactory schemaFactory = new XMLSyntaxSchemaFactory();
        assertThat(schemaFactory.isSchemaLanguageSupported(XMLSyntaxSchemaFactory.SCHEMA_LANGUAGE))
                .isTrue();
        Schema schema = schemaFactory.newSchema(source(INTERLEAVED_CATALOG_SCHEMA));
        Validator validator = schema.newValidator();

        assertThatCode(() -> validator.validate(source(
                        "<catalog><updated>2025-01-01</updated><title>Native XML</title></catalog>")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(source("<catalog><title>Incomplete</title></catalog>")))
                .isInstanceOf(SAXException.class);
    }

    private static StreamSource source(String contents) {
        return new StreamSource(new StringReader(contents));
    }
}
