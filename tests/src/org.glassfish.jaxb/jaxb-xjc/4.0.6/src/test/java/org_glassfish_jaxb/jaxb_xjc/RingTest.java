/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.tools.xjc.api.S2JJAXBModel;
import com.sun.tools.xjc.api.SchemaCompiler;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RingTest {
    @Test
    void compilerScopesDoNotLeakSchemaComponents() {
        for (String element : new String[]{"first", "second"}) {
            XjcTestSupport.Diagnostics diagnostics = new XjcTestSupport.Diagnostics();
            SchemaCompiler compiler = XjcTestSupport.compiler(diagnostics);
            compiler.parseSchema(XjcTestSupport.input(element + ".xsd", XjcTestSupport.schema("""
                    <xs:element name="%s">
                      <xs:complexType><xs:attribute name="code" type="xs:string"/></xs:complexType>
                    </xs:element>
                    """.formatted(element))));
            S2JJAXBModel model = compiler.bind();
            assertNotNull(model, diagnostics.errors.toString());
            assertEquals(1, model.getMappings().size());
            assertNotNull(model.get(new QName(element)));
            assertTrue(diagnostics.errors.isEmpty());
        }
    }
}
