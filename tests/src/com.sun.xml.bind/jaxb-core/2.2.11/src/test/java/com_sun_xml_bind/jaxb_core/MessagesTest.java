/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_core;

import com.sun.xml.bind.marshaller.Messages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagesTest {
    @Test
    void formatsAMarshallerDiagnosticWithItsArgument() {
        assertThat(Messages.format(Messages.UNSUPPORTED_ENCODING, "example-encoding"))
                .contains("example-encoding")
                .doesNotContain("{0}")
                .isNotEqualTo(Messages.UNSUPPORTED_ENCODING);
    }
}
