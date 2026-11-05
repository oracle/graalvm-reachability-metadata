/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import org.apache.logging.log4j.message.ObjectArrayMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectArrayMessageTest {

    @Test
    void retainsStructuredParametersAfterSerialization() throws Exception {
        ObjectArrayMessage original = new ObjectArrayMessage("order-7", Integer.valueOf(4), Boolean.TRUE, null);

        ObjectArrayMessage copy = SerializationSupport.roundTrip(original, ObjectArrayMessage.class);

        assertThat(copy.getParameters()).containsExactly("order-7", Integer.valueOf(4), Boolean.TRUE, null);
        assertThat(copy.getFormattedMessage()).isEqualTo("[order-7, 4, true, null]");
        assertThat(copy.getFormat()).isEqualTo(original.getFormat());
        assertThat(copy).isEqualTo(original);
        assertThat(copy.getThrowable()).isNull();
    }
}
