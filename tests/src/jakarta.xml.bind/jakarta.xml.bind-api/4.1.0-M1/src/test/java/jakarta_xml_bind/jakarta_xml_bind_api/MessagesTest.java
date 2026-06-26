/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_bind.jakarta_xml_bind_api;

import jakarta.xml.bind.PropertyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagesTest {
    @Test
    public void formatsPropertyExceptionMessagesFromTheResourceBundle() {
        PropertyException exception = new PropertyException("example.property", 7);

        assertThat(exception.getMessage()).contains("example.property").contains("7");
    }
}
