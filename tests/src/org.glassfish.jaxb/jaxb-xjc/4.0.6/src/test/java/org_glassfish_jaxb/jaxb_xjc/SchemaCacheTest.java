/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.tools.xjc.SchemaCache;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BindInfo;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.ValidatorHandler;
import org.junit.jupiter.api.Test;
import org.xml.sax.XMLReader;

import static org.junit.jupiter.api.Assertions.assertNotSame;

public class SchemaCacheTest {
    @Test
    void cachedBindingSchemaCreatesIndependentValidators() throws Exception {
        SchemaCache cache = new SchemaCache("binding.xsd", BindInfo.class, true);
        ValidatorHandler first = cache.newValidator();
        ValidatorHandler second = cache.newValidator();
        assertNotSame(first, second);
        validate(first, """
                <globalBindings xmlns="https://jakarta.ee/xml/ns/jaxb" generateIsSetMethod="true"/>
                """);
        validate(second, """
                <schemaBindings xmlns="https://jakarta.ee/xml/ns/jaxb">
                  <package name="example.customer"/>
                </schemaBindings>
                """);
    }

    static void validate(ValidatorHandler validator, String document) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setContentHandler(validator);
        reader.parse(XjcTestSupport.input("bindings.xml", document));
    }
}
