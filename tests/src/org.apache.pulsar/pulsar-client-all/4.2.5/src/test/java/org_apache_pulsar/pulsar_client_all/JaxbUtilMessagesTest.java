/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.pulsar.shade.javax.xml.bind.JAXBContext;
import org.apache.pulsar.shade.javax.xml.bind.JAXBException;
import org.apache.pulsar.shade.javax.xml.bind.util.JAXBResult;
import org.junit.jupiter.api.Test;

public class JaxbUtilMessagesTest {
    @Test
    void rejectsMissingContextWithLocalizedMessage() {
        assertThatThrownBy(() -> new JAXBResult((JAXBContext) null))
                .isInstanceOf(JAXBException.class)
                .hasMessageContaining("null");
    }
}
