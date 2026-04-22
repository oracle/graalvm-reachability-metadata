/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api;

import javax.xml.bind.helpers.ValidationEventImpl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HelpersMessagesTest {
    @Test
    public void formatsHelperMessagesForIllegalSeverityValues() {
        assertThatThrownBy(() -> new ValidationEventImpl(99, "boom", null))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }
}
