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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FieldMessagesTest {
    @TempDir
    Path directory;

    @Test
    void repeatedElementsGetDocumentedLiveListAccessors() throws Exception {
        JCodeModel model = XjcTestSupport.compile(XjcTestSupport.schema("""
                <xs:complexType name="Basket">
                  <xs:sequence>
                    <xs:element name="item" type="xs:string" maxOccurs="unbounded"/>
                  </xs:sequence>
                </xs:complexType>
                """), new XjcTestSupport.Diagnostics());
        String source = XjcTestSupport.source(model, directory, "Basket");
        assertTrue(source.contains("List<String> getItem()"));
        assertTrue(source.contains("@return"));
        assertTrue(source.contains("getItem().add(newItem)"), source);
        assertFalse(source.contains("void setItem("));
    }
}
