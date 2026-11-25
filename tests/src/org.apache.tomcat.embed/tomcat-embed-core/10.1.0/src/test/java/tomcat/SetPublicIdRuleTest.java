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

public class SetPublicIdRuleTest {
    @Test
    void recordsPublicIdWhenReadingLegacyDeploymentDescriptor() throws Exception {
        WebXml descriptor = new WebXml();
        Digester digester = DigesterFactory.newDigester(false, false, new WebRuleSet(), true);
        digester.push(descriptor);
        String xml = """
                <!DOCTYPE web-app PUBLIC "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
                    "http://java.sun.com/dtd/web-app_2_3.dtd">
                <web-app><display-name>legacy-app</display-name></web-app>
                """;
        digester.parse(new InputSource(new StringReader(xml)));
        assertEquals("-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN", descriptor.getPublicId());
        assertEquals("legacy-app", descriptor.getDisplayName());
    }
}
