/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xercesImpl;

import org.apache.xerces.dom.DOMMessageFormatter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class DOMMessageFormatterTest {
    @Test
    void formatsDomMessagesUsingTheDomResourceBundle() {
        String message = DOMMessageFormatter.formatMessage(
                DOMMessageFormatter.DOM_DOMAIN, "NO_MODIFICATION_ALLOWED_ERR", null);

        Assertions.assertThat(message).isEqualTo(
                "NO_MODIFICATION_ALLOWED_ERR: An attempt is made to modify an object where modifications are not allowed.");
    }

    @Test
    void formatsSerializerMessagesUsingTheSerializerResourceBundle() {
        String message = DOMMessageFormatter.formatMessage(
                DOMMessageFormatter.SERIALIZER_DOMAIN, "MethodNotSupported", new Object[] {"json"});

        Assertions.assertThat(message).isEqualTo(
                "MethodNotSupported: The method 'json' is not supported by this factory.");
    }
}
