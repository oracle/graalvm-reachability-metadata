/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xercesImpl;

import java.util.Locale;

import org.apache.xerces.impl.msg.XMLMessageFormatter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class XMLMessageFormatterTest {
    @Test
    void formatsMessagesUsingTheDefaultResourceBundle() {
        XMLMessageFormatter formatter = new XMLMessageFormatter();

        String message = formatter.formatMessage(null, "RootElementRequired", null);

        Assertions.assertThat(message).isEqualTo(
                "The root element is required in a well-formed document.");
    }

    @Test
    void formatsMessagesUsingTheRequestedLocaleResourceBundle() {
        XMLMessageFormatter formatter = new XMLMessageFormatter();

        String message = formatter.formatMessage(
                Locale.ENGLISH, "InvalidCharInContent", new Object[] {"20"});

        Assertions.assertThat(message).isEqualTo(
                "An invalid XML character (Unicode: 0x20) was found in the element content of the document.");
    }
}
