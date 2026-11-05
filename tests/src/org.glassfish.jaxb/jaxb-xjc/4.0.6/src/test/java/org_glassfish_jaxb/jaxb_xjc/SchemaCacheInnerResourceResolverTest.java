/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.tools.xjc.SchemaCache;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BindInfo;
import javax.xml.validation.ValidatorHandler;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class SchemaCacheInnerResourceResolverTest {
    @Test
    void bindingValidationResolvesImportedExtensionSchemas() throws Exception {
        SchemaCache cache = new SchemaCache("binding.xsd", BindInfo.class, true);
        ValidatorHandler validator = cache.newValidator();
        SchemaCacheTest.validate(validator, """
                <globalBindings xmlns="https://jakarta.ee/xml/ns/jaxb"
                                xmlns:xjc="http://java.sun.com/xml/ns/jaxb/xjc">
                  <xjc:superClass name="example.Base"/>
                </globalBindings>
                """);
        assertThrows(SAXException.class, () -> SchemaCacheTest.validate(cache.newValidator(), """
                <globalBindings xmlns="https://jakarta.ee/xml/ns/jaxb"
                                xmlns:xjc="http://java.sun.com/xml/ns/jaxb/xjc">
                  <xjc:superClass/>
                </globalBindings>
                """));
    }
}
