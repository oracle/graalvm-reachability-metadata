/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.tools.xjc.api.SchemaCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReaderMessagesTest {
    @Test
    void undeclaredExtensionPrefixesAreDiagnosed() {
        XjcTestSupport.Diagnostics diagnostics = new XjcTestSupport.Diagnostics();
        SchemaCompiler compiler = XjcTestSupport.compiler(diagnostics);
        compiler.parseSchema(XjcTestSupport.input("schema.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:jaxb="https://jakarta.ee/xml/ns/jaxb" jaxb:version="3.0"
                           jaxb:extensionBindingPrefixes="missingPrefix">
                  <xs:element name="entry" type="xs:string"/>
                </xs:schema>
                """));
        assertNull(compiler.bind());
        assertTrue(diagnostics.errors.stream().anyMatch(message -> message.contains("missingPrefix")),
                diagnostics.errors.toString());
    }
}
