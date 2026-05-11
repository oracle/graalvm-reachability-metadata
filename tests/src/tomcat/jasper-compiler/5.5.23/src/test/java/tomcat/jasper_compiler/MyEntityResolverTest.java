/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.jasper.xmlparser.ParserUtils;
import org.apache.jasper.xmlparser.TreeNode;
import org.junit.jupiter.api.Test;

public class MyEntityResolverTest {
    @Test
    void parserUsesCachedDtdResourceWhenResolvingKnownPublicId() throws Exception {
        final boolean previousValidating = ParserUtils.validating;
        ParserUtils.validating = true;
        try {
            final String webXml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE web-app PUBLIC "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
                            "http://java.sun.com/dtd/web-app_2_3.dtd">
                    <web-app>
                        <display-name>coverage</display-name>
                    </web-app>
                    """;
            final ByteArrayInputStream input = new ByteArrayInputStream(webXml.getBytes(StandardCharsets.UTF_8));

            final TreeNode root = new ParserUtils().parseXMLDocument("web.xml", input);

            assertThat(root.getName()).isEqualTo("web-app");
            assertThat(root.findChild("display-name").getBody()).isEqualTo("coverage");
        } finally {
            ParserUtils.validating = previousValidating;
        }
    }
}
