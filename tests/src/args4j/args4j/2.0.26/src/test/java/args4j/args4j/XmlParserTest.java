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

public class XmlParserTest {
    @Test
    void parsesXmlConfiguredFieldAndMethodOptions() throws Exception {
        XmlConfiguredOptions options = new XmlConfiguredOptions();
        CmdLineParser parser = new CmdLineParser(null);

        new XmlParser().parse(xmlSource("""
                <args>
                    <option field="name" name="-name" usage="sets the name"/>
                    <option method="setCount(Integer)" name="-count" usage="sets the count"/>
                </args>
                """), parser, options);
        parser.parseArgument("-name", "args4j", "-count", "26");

        assertThat(options.name).isEqualTo("args4j");
        assertThat(options.count).isEqualTo(26);
    }

    private static InputSource xmlSource(String xml) {
        return new InputSource(new StringReader(xml));
    }

    public static class XmlConfiguredOptions {
        public String name;
        private Integer count;

        public void setCount(Integer count) {
            this.count = count;
        }
    }
}
