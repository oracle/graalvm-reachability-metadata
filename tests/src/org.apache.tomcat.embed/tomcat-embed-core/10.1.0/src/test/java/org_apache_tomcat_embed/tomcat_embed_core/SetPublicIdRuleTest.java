/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.StringReader;

import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.descriptor.web.WebXmlParser;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class SetPublicIdRuleTest {

    @Test
    void recordsDescriptorPublicIdDuringWebXmlParsing() {
        String publicId = "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN";
        InputSource source = new InputSource(new StringReader("""
                <!DOCTYPE web-app PUBLIC "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
                        "http://java.sun.com/dtd/web-app_2_3.dtd">
                <web-app/>
                """));
        source.setSystemId("web.xml");
        WebXml webXml = new WebXml();
        WebXmlParser parser = new WebXmlParser(false, false, true);

        assertThat(parser.parseWebXml(source, webXml, false)).isTrue();
        assertThat(webXml.getPublicId()).isEqualTo(publicId);
    }
}
