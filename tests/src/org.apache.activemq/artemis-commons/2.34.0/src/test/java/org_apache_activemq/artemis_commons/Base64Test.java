/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.artemis.utils.Base64;
import org.junit.jupiter.api.Test;

public class Base64Test {
    @Test
    public void encodesAndDecodesSerializableObject() {
        String payload = "active-mq-artemis";

        String encoded = Base64.encodeObject(payload, Base64.DONT_BREAK_LINES);
        Object decoded = Base64.decodeToObject(encoded);

        assertThat(encoded).isNotBlank();
        assertThat(decoded).isEqualTo(payload);
    }
}
