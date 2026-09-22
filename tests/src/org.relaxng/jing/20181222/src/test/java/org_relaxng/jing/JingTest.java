/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_relaxng.jing;

import static org.assertj.core.api.Assertions.assertThat;

import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.Schema;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.Validator;
import com.thaiopensource.validate.rng.CompactSchemaReader;
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

public class JingTest {
    private static final String RELAX_NG_SCHEMA = """
            <grammar xmlns="http://relaxng.org/ns/structure/1.0"
                     datatypeLibrary="http://www.w3.org/2001/XMLSchema-datatypes">
              <start>
                <element name="order">
                  <attribute name="id"><data type="positiveInteger"/></attribute>
                  <oneOrMore>
                    <element name="item">
                      <attribute name="sku"><data type="token"/></attribute>
                      <text/>
                    </element>
                  </oneOrMore>
                </element>
              </start>
            </grammar>
            """;

    private static final String COMPACT_SCHEMA = """
            datatypes xsd = "http://www.w3.org/2001/XMLSchema-datatypes"
            start = element inventory {
              element product {
                attribute code { xsd:token },
                attribute quantity { xsd:nonNegativeInteger },
                element description { text }
              }+
            }
            """;

    @Test
    void validatesRelaxNgXmlSyntaxWithXmlSchemaDatatypes() throws Exception {
        Schema schema = createSchema(SAXSchemaReader.getInstance(), RELAX_NG_SCHEMA, "memory:/order.rng");

        assertThat(validationErrors(
                        schema,
                        "<order id=\"42\"><item sku=\" A-1 \">Keyboard</item></order>",
                        "memory:/valid-order.xml"))
                .isEmpty();
        assertThat(validationErrors(
                        schema,
                        "<order id=\"zero\"><unexpected/></order>",
                        "memory:/invalid-order.xml"))
                .isNotEmpty();
    }

    @Test
    void validatesRelaxNgCompactSyntax() throws Exception {
        Schema schema = createSchema(
                CompactSchemaReader.getInstance(), COMPACT_SCHEMA, "memory:/inventory.rnc");

        assertThat(validationErrors(
                        schema,
                        """
                        <inventory>
                          <product code="P-100" quantity="3">
                            <description>Native Image guide</description>
                          </product>
                        </inventory>
                        """,
                        "memory:/valid-inventory.xml"))
                .isEmpty();
        assertThat(validationErrors(
                        schema,
                        "<inventory><product code=\"P-100\" quantity=\"many\"/></inventory>",
                        "memory:/invalid-inventory.xml"))
                .isNotEmpty();
    }

    private static Schema createSchema(SchemaReader reader, String schema, String systemId)
            throws Exception {
        CollectingErrorHandler errors = new CollectingErrorHandler();
        Schema result = reader.createSchema(source(schema, systemId), propertiesWith(errors).toPropertyMap());
        assertThat(errors.messages()).isEmpty();
        return result;
    }

    private static List<String> validationErrors(Schema schema, String document, String systemId)
            throws Exception {
        CollectingErrorHandler errors = new CollectingErrorHandler();
        Validator validator = schema.createValidator(propertiesWith(errors).toPropertyMap());
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        XMLReader reader = parserFactory.newSAXParser().getXMLReader();
        reader.setContentHandler(validator.getContentHandler());
        reader.setDTDHandler(validator.getDTDHandler());
        reader.setErrorHandler(errors);
        reader.parse(source(document, systemId));
        return errors.messages();
    }

    private static PropertyMapBuilder propertiesWith(CollectingErrorHandler errors) {
        PropertyMapBuilder properties = new PropertyMapBuilder();
        properties.put(ValidateProperty.ERROR_HANDLER, errors);
        return properties;
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
