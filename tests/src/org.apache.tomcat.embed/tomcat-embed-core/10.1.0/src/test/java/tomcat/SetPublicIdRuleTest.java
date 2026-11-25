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

public class SetPublicIdRuleTest {
    @Test
    void recordsPublicIdentifierFromLegacyWebDescriptor() {
        WebXml descriptor = new WebXml();
        InputSource source = new InputSource(new StringReader("""
                <!DOCTYPE web-app PUBLIC "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
                    "http://java.sun.com/dtd/web-app_2_3.dtd">
                <web-app><display-name>Legacy catalog</display-name></web-app>
                """));
        assertThat(new WebXmlParser(false, true, true).parseWebXml(source, descriptor, false)).isTrue();
        assertThat(descriptor.getPublicId()).isEqualTo("-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN");
        assertThat(descriptor.getDisplayName()).isEqualTo("Legacy catalog");
    }
}
