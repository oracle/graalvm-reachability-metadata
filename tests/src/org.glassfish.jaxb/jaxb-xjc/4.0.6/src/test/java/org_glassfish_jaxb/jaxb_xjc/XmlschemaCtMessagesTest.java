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

public class XmlschemaCtMessagesTest {
    @Test
    void extensionCannotReintroduceRestrictedBaseContent() {
        XjcTestSupport.Diagnostics diagnostics = new XjcTestSupport.Diagnostics();
        SchemaCompiler compiler = XjcTestSupport.compiler(diagnostics);
        compiler.parseSchema(XjcTestSupport.input("schema.xsd", XjcTestSupport.schema("""
                <xs:complexType name="BaseRecord">
                  <xs:sequence>
                    <xs:element name="first" type="xs:string" minOccurs="0"/>
                    <xs:element name="second" type="xs:string" minOccurs="0"/>
                  </xs:sequence>
                </xs:complexType>
                <xs:complexType name="RestrictedRecord">
                  <xs:complexContent><xs:restriction base="BaseRecord">
                    <xs:sequence><xs:element name="first" type="xs:string" minOccurs="0"/></xs:sequence>
                  </xs:restriction></xs:complexContent>
                </xs:complexType>
                <xs:complexType name="ExtendedRecord">
                  <xs:complexContent><xs:extension base="RestrictedRecord">
                    <xs:sequence><xs:element name="second" type="xs:string" minOccurs="0"/></xs:sequence>
                  </xs:extension></xs:complexContent>
                </xs:complexType>
                """)));
        assertNull(compiler.bind());
        assertTrue(diagnostics.errors.stream().anyMatch(message -> message.contains("RestrictedRecord")
                && message.contains("ExtendedRecord")), diagnostics.errors.toString());
    }
}
