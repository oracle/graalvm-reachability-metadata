/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xercesImpl;

import java.util.Locale;

import org.apache.xerces.impl.xs.XSMessageFormatter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class XSMessageFormatterTest {
    @Test
    void formatsSchemaMessagesUsingTheDefaultResourceBundle() {
        XSMessageFormatter formatter = new XSMessageFormatter();

        String message = formatter.formatMessage(null, "Internal-Error", new Object[] {"details"});

        Assertions.assertThat(message).isEqualTo("Internal error: details.");
    }

    @Test
    void formatsSchemaMessagesUsingTheRequestedLocaleResourceBundle() {
        XSMessageFormatter formatter = new XSMessageFormatter();

        String message = formatter.formatMessage(Locale.ENGLISH, "Internal-Error", new Object[] {"details"});

        Assertions.assertThat(message).isEqualTo("Internal error: details.");
    }
}
