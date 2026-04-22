/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_bind.jakarta_xml_bind_api;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.util.JAXBResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UtilMessagesTest {
    @Test
    public void formatsUtilMessagesFromTheResourceBundle() {
        assertThatThrownBy(() -> new JAXBResult((JAXBContext) null))
                .isInstanceOf(JAXBException.class);
    }
}
