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

public class XmlschemaMessagesTest {
    @TempDir
    Path directory;

    @Test
    void generatedClassDocumentsItsSchemaType() throws Exception {
        JCodeModel model = XjcTestSupport.compile(XjcTestSupport.schema("""
                <xs:complexType name="Address">
                  <xs:sequence><xs:element name="city" type="xs:string"/></xs:sequence>
                </xs:complexType>
                """), new XjcTestSupport.Diagnostics());
        String source = XjcTestSupport.source(model, directory, "Address");
        assertTrue(source.contains("Address complex type"), source);
        assertTrue(source.contains("String getCity()"));
    }
}
