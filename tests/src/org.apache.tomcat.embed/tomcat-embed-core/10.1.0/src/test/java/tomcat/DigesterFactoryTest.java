/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.StringReader;

import org.apache.tomcat.util.descriptor.DigesterFactory;
import org.apache.tomcat.util.descriptor.web.WebRuleSet;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.digester.Digester;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DigesterFactoryTest {
    @Test
    void readsNamespacedDeploymentDescriptorWithLocalSchemas() throws Exception {
        WebXml descriptor = new WebXml();
        Digester digester = DigesterFactory.newDigester(true, true, new WebRuleSet(), true);
        digester.push(descriptor);
        String xml = """
                <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd"
                    version="4.0" metadata-complete="true">
                  <display-name>catalog</display-name>
                </web-app>
                """;
        digester.parse(new InputSource(new StringReader(xml)));
        assertEquals("catalog", descriptor.getDisplayName());
        assertTrue(descriptor.isMetadataComplete());
    }
}
