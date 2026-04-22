/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.util.JAXBResult;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UtilMessagesTest {
    @Test
    public void formatsUtilityMessagesForNullContext() {
        assertThatThrownBy(() -> new JAXBResult((JAXBContext) null))
                .isInstanceOf(JAXBException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }
}
