/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xercesImpl;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

public class XMLSchemaLoaderTest {
    private static final String JAXP_SCHEMA_SOURCE =
            Constants.JAXP_PROPERTY_PREFIX + Constants.SCHEMA_SOURCE;

    @Test
    void loadsSchemaWithJaxpSchemaSourceArray() throws Exception {
        XMLSchemaLoader loader = new XMLSchemaLoader();
        InputSource preloadedSchema = new InputSource(new ByteArrayInputStream(schema("urn:preloaded").getBytes(
                StandardCharsets.UTF_8)));
        loader.setProperty(JAXP_SCHEMA_SOURCE, new Object[] {preloadedSchema});

        XMLInputSource requestedSchema = new XMLInputSource(null, null, null,
                new ByteArrayInputStream(schema("urn:requested").getBytes(StandardCharsets.UTF_8)), "UTF-8");
        Grammar grammar = loader.loadGrammar(requestedSchema);

        Assertions.assertThat(grammar).isNotNull();
    }

    private static String schema(String namespace) {
        return """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="%s"
                           xmlns="%s"
                           elementFormDefault="qualified">
                    <xs:element name="message" type="xs:string"/>
                </xs:schema>
                """.formatted(namespace, namespace);
    }
}
