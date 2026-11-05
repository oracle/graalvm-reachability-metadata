/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.codemodel.JCodeModel;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GenericFieldRendererTest {
    @TempDir
    Path directory;

    @Test
    void generatesPrimitiveAndReferenceAccessors() throws Exception {
        JCodeModel model = XjcTestSupport.compile(XjcTestSupport.schema("""
                <xs:complexType name="Invoice">
                  <xs:sequence>
                    <xs:element name="count" type="xs:int"/>
                    <xs:element name="description" type="xs:string" minOccurs="0"/>
                    <xs:element name="active" type="xs:boolean"/>
                  </xs:sequence>
                </xs:complexType>
                """), new XjcTestSupport.Diagnostics());
        String source = XjcTestSupport.source(model, directory, "Invoice");
        assertTrue(source.contains("int getCount()"));
        assertTrue(source.contains("void setCount(int value)"));
        assertTrue(source.contains("String getDescription()"));
        assertTrue(source.contains("boolean isActive()"));
    }
}
