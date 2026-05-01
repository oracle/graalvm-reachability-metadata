/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.neko_htmlunit;

import net.sourceforge.htmlunit.xerces.impl.msg.XMLMessageFormatter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XMLMessageFormatterTest {

    @Test
    void formatsXmlMessagesFromResourceBundle() {
        final XMLMessageFormatter formatter = new XMLMessageFormatter();

        final String message = formatter.formatMessage("RootElementRequired", null);

        assertThat(message).contains("root element");
    }
}
