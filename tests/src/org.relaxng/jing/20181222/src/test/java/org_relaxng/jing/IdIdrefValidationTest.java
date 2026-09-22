/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_relaxng.jing;

import static org.assertj.core.api.Assertions.assertThat;

import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.Flag;
import com.thaiopensource.validate.Schema;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.Validator;
import com.thaiopensource.validate.prop.rng.RngProperty;
import com.thaiopensource.validate.rng.SAXSchemaReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.SAXParserFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class IdIdrefValidationTest {
    private static final String REFERENCE_SCHEMA = """
            <element xmlns="http://relaxng.org/ns/structure/1.0"
                     name="catalog"
                     datatypeLibrary="http://www.w3.org/2001/XMLSchema-datatypes">
              <zeroOrMore>
                <element name="entry">
                  <attribute name="id"><data type="ID"/></attribute>
                  <optional>
                    <attribute name="related"><data type="IDREF"/></attribute>
                  </optional>
                  <empty/>
                </element>
              </zeroOrMore>
            </element>
            """;

    @Test
    void checksIdReferencesUsingDtdCompatibilityRules() throws Exception {
        CollectingErrorHandler schemaErrors = new CollectingErrorHandler();
        PropertyMapBuilder schemaProperties = new PropertyMapBuilder();
        schemaProperties.put(ValidateProperty.ERROR_HANDLER, schemaErrors);
        schemaProperties.put(RngProperty.CHECK_ID_IDREF, Flag.PRESENT);
        Schema schema = SAXSchemaReader.getInstance()
                .createSchema(source(REFERENCE_SCHEMA, "memory:/references.rng"), schemaProperties.toPropertyMap());

        assertThat(schemaErrors.messages()).isEmpty();
        assertThat(validationErrors(
                        schema,
                        "<catalog><entry id=\"root\"/><entry id=\"child\" related=\"root\"/></catalog>"))
                .isEmpty();
        assertThat(validationErrors(
                        schema,
                        "<catalog><entry id=\"root\"/><entry id=\"child\" related=\"missing\"/></catalog>"))
                .isNotEmpty();
    }

    private static List<String> validationErrors(Schema schema, String document) throws Exception {
        CollectingErrorHandler errors = new CollectingErrorHandler();
        PropertyMapBuilder properties = new PropertyMapBuilder();
        properties.put(ValidateProperty.ERROR_HANDLER, errors);
        Validator validator = schema.createValidator(properties.toPropertyMap());
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        XMLReader reader = parserFactory.newSAXParser().getXMLReader();
        reader.setContentHandler(validator.getContentHandler());
        reader.setDTDHandler(validator.getDTDHandler());
        reader.setErrorHandler(errors);
        reader.parse(source(document, "memory:/catalog.xml"));
        return errors.messages();
    }

    private static InputSource source(String contents, String systemId) {
        InputSource source = new InputSource(new StringReader(contents));
        source.setSystemId(systemId);
        return source;
    }

    private static final class CollectingErrorHandler extends DefaultHandler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void error(SAXParseException exception) {
            messages.add(exception.getMessage());
        }

        @Override
        public void fatalError(SAXParseException exception) {
            messages.add(exception.getMessage());
        }

        List<String> messages() {
            return messages;
        }
    }
}
