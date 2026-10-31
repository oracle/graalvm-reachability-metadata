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

public class UtilMessagesTest {
    @Test
    void schemaTypesThatMapToTheSameClassAreRejected() {
        XjcTestSupport.Diagnostics diagnostics = new XjcTestSupport.Diagnostics();
        SchemaCompiler compiler = XjcTestSupport.compiler(diagnostics);
        compiler.parseSchema(XjcTestSupport.input("schema.xsd", XjcTestSupport.schema("""
                <xs:complexType name="foo-bar"><xs:attribute name="a" type="xs:string"/></xs:complexType>
                <xs:complexType name="foo_bar"><xs:attribute name="b" type="xs:int"/></xs:complexType>
                """)));
        assertNull(compiler.bind());
        assertTrue(diagnostics.errors.stream().anyMatch(message -> message.contains("generated.FooBar")),
                diagnostics.errors.toString());
        assertTrue(diagnostics.errors.size() >= 2, diagnostics.errors.toString());
    }
}
