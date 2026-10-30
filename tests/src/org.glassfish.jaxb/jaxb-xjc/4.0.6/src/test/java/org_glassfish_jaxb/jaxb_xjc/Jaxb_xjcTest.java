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

public class Jaxb_xjcTest {
    @Test
    void mapsXmlElementsToGeneratedJavaTypes() {
        XjcTestSupport.Diagnostics diagnostics = new XjcTestSupport.Diagnostics();
        SchemaCompiler compiler = XjcTestSupport.compiler(diagnostics);
        compiler.parseSchema(XjcTestSupport.input("schema.xsd", XjcTestSupport.schema("""
                <xs:element name="purchase">
                  <xs:complexType><xs:attribute name="number" type="xs:int"/></xs:complexType>
                </xs:element>
                """)));
        S2JJAXBModel model = compiler.bind();
        assertNotNull(model, diagnostics.errors.toString());
        assertNotNull(model.get(new QName("purchase")));
        assertEquals("generated.Purchase", model.get(new QName("purchase")).getType().getTypeClass().fullName());
        assertEquals("generated.ObjectFactory", model.getAllObjectFactories().get(0).fullName());
    }
}
