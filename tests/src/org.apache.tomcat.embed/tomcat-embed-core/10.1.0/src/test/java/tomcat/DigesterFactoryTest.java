/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.StringReader;
import org.xml.sax.InputSource;

import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.descriptor.web.WebXmlParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DigesterFactoryTest {
    @Test
    void validatesWebDescriptorUsingBundledSchemaWithoutNetwork() {
        WebXml descriptor = new WebXml();
        WebXmlParser parser = new WebXmlParser(true, true, true);
        InputSource source = new InputSource(new StringReader("""
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
                         version="6.0">
                  <display-name>Catalog application</display-name>
                </web-app>
                """));
        assertThat(parser.parseWebXml(source, descriptor, false)).isTrue();
        assertThat(descriptor.getDisplayName()).isEqualTo("Catalog application");
    }
}
