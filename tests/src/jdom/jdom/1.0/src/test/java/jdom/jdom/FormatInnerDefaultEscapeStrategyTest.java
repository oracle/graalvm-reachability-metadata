/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import org.jdom.output.EscapeStrategy;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.jupiter.api.Test;

public class FormatInnerDefaultEscapeStrategyTest {
    private static final String ENCODER_BACKED_ENCODING = "UTF-16BE";

    @Test
    void nonShortcutEncodingUsesCharsetEncoderBackedEscapeStrategy() {
        Format format = Format.getRawFormat().setEncoding(ENCODER_BACKED_ENCODING);

        EscapeStrategy escapeStrategy = format.getEscapeStrategy();

        assertThat(format.getEncoding()).isEqualTo(ENCODER_BACKED_ENCODING);
        assertThat(escapeStrategy.shouldEscape('A')).isFalse();
        assertThat(escapeStrategy.shouldEscape('\u03A9')).isFalse();
    }

    @Test
    void encoderBackedEscapeStrategyIsUsedByXmlOutputter() {
        Format format = Format.getRawFormat().setEncoding(ENCODER_BACKED_ENCODING);
        XMLOutputter outputter = new XMLOutputter(format);

        String escaped = outputter.escapeElementEntities("Text with \u03A9 and <tag> & data");

        assertThat(escaped).isEqualTo("Text with \u03A9 and &lt;tag&gt; &amp; data");
    }
}
