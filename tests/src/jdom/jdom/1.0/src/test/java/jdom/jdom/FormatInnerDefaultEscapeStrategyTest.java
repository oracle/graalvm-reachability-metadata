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
import org.junit.jupiter.api.Test;

public class FormatInnerDefaultEscapeStrategyTest {
    @Test
    void charsetBackedEscapeStrategyEscapesCharactersNotSupportedByEncoding() {
        Format format = Format.getRawFormat().setEncoding("ISO-8859-2");
        EscapeStrategy escapeStrategy = format.getEscapeStrategy();

        assertThat(escapeStrategy.shouldEscape('A')).isFalse();
        assertThat(escapeStrategy.shouldEscape('\u20ac')).isTrue();
    }
}
