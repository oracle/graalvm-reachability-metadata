/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api;

import javax.xml.bind.PropertyException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagesTest {
    @Test
    public void formatsPropertyMessages() {
        PropertyException exception = new PropertyException("property-name", 42);

        assertThat(exception.getMessage()).contains("property-name").contains("42");
    }
}
