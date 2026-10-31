/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.codemodel.JCodeModel;
import com.sun.tools.xjc.addon.accessors.PluginImpl;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AccessorsPluginImplTest {
    @TempDir
    Path directory;

    @Test
    void pluginSelectsPropertyAccessForGeneratedBeans() throws Exception {
        JCodeModel model = XjcTestSupport.compile(XjcTestSupport.schema("""
                <xs:complexType name="Customer">
                  <xs:sequence><xs:element name="name" type="xs:string"/></xs:sequence>
                </xs:complexType>
                """), new XjcTestSupport.Diagnostics(), new PluginImpl());
        String source = XjcTestSupport.source(model, directory, "Customer");
        assertTrue(source.contains("@XmlAccessorType(XmlAccessType.PROPERTY)"), source);
        assertFalse(source.contains("XmlAccessType.FIELD"));
        assertTrue(source.contains("String getName()"));
    }
}
