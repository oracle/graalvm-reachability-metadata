/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.pulsar.shade.javax.xml.bind.helpers.ValidationEventImpl;
import org.junit.jupiter.api.Test;

public class JaxbHelpersMessagesTest {
    @Test
    void rejectsUnknownValidationSeverityWithLocalizedMessage() {
        assertThatThrownBy(() -> new ValidationEventImpl(99, "invalid", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("severity");
    }
}
