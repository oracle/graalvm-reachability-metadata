/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package args4j.args4j;

import java.io.StringReader;

import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.XmlParser;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationImplTest {
    @Test
    void loadsConfiguredOptionHandlerFromXmlAnnotationMetadata() throws Exception {
        HandlerConfiguredBean bean = new HandlerConfiguredBean();
        CmdLineParser parser = new CmdLineParser(null);
        XmlParser xmlParser = new XmlParser();
        String xml = """
                <args>
                    <option
                        name="-value"
                        field="value"
                        handler="org.kohsuke.args4j.spi.StringOptionHandler"
                        usage="value parsed with an explicitly configured handler"/>
                </args>
                """;

        xmlParser.parse(new InputSource(new StringReader(xml)), parser, bean);
        parser.parseArgument("-value", "configured-handler");

        assertThat(bean.value()).isEqualTo("configured-handler");
    }

    public static class HandlerConfiguredBean {
        private String value;

        String value() {
            return value;
        }
    }
}
