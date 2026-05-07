/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.junit.jupiter.api.Test;

public class FormatInnerDefaultEscapeStrategyTest {
    @Test
    void configuredOutputterUsesInnerFormatSettingsAndEscapesXmlEntities() {
        XMLOutputter outputter = new XMLOutputter();
        outputter.setIndent("  ");
        outputter.setNewlines(true);
        outputter.setLineSeparator("\n");

        Element catalog = new Element("catalog")
                .addContent(new Element("title").setText("JDOM & Native Image"));

        assertThat(outputter.outputString(catalog)).isEqualTo("""
                <catalog>
                  <title>JDOM &amp; Native Image</title>
                </catalog>""".stripIndent());
    }
}
