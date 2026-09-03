/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_bind.jakarta_xml_bind_api;

import jakarta.xml.bind.helpers.ValidationEventLocatorImpl;
import org.junit.jupiter.api.Test;
import org.xml.sax.Locator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HelpersMessagesTest {
    @Test
    public void formatsHelperMessagesFromTheResourceBundle() {
        assertThatThrownBy(() -> new ValidationEventLocatorImpl((Locator) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loc");
    }
}
