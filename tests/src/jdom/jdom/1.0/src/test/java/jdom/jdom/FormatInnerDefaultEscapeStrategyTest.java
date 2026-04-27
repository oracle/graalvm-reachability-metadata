/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FormatInnerDefaultEscapeStrategyTest {
    @Test
    void defaultEscapeStrategyUsesCharsetEncoderForNonBuiltInEncodings() {
        Format format = Format.getRawFormat().setEncoding("windows-1252");
        XMLOutputter outputter = new XMLOutputter(format);

        String escapedText = outputter.escapeElementEntities("cafe \u00e9 and snowman \u2603");

        assertThat(escapedText).isEqualTo("cafe \u00e9 and snowman &#x2603;");
    }
}
