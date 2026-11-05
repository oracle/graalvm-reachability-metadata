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

public class InternalizerMessagesTest {
    @Test
    void externalBindingXPathMustSelectASchemaNode() {
        XjcTestSupport.Diagnostics diagnostics = new XjcTestSupport.Diagnostics();
        SchemaCompiler compiler = XjcTestSupport.compiler(diagnostics);
        compiler.parseSchema(XjcTestSupport.input("schema.xsd", XjcTestSupport.schema("""
                <xs:element name="present" type="xs:string"/>
                """)));
        compiler.parseSchema(XjcTestSupport.input("bindings.xjb", """
                <jaxb:bindings xmlns:jaxb="https://jakarta.ee/xml/ns/jaxb"
                               xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0"
                               schemaLocation="schema.xsd" node="/xs:schema">
                  <jaxb:bindings node="xs:element[@name='absentEntry']">
                    <jaxb:class name="Renamed"/>
                  </jaxb:bindings>
                </jaxb:bindings>
                """));
        assertNull(compiler.bind());
        assertTrue(diagnostics.errors.stream().anyMatch(message -> message.contains("absentEntry")),
                diagnostics.errors.toString());
    }
}
