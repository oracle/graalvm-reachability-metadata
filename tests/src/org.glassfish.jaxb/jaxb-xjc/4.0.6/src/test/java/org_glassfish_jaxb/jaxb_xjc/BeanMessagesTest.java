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

public class BeanMessagesTest {
    @TempDir
    Path directory;

    @Test
    void enumerationIncludesConversionMethodsAndDocumentation() throws Exception {
        JCodeModel model = XjcTestSupport.compile(XjcTestSupport.schema("""
                <xs:simpleType name="Priority">
                  <xs:restriction base="xs:string">
                    <xs:enumeration value="low"/><xs:enumeration value="high"/>
                  </xs:restriction>
                </xs:simpleType>
                """), new XjcTestSupport.Diagnostics());
        String source = XjcTestSupport.source(model, directory, "Priority");
        assertTrue(source.contains("LOW(\"low\")"));
        assertTrue(source.contains("HIGH(\"high\")"));
        assertTrue(source.contains("Priority fromValue(String v)"));
        assertTrue(source.contains("@return"));
        assertTrue(source.contains("@throws IllegalArgumentException"));
    }
}
