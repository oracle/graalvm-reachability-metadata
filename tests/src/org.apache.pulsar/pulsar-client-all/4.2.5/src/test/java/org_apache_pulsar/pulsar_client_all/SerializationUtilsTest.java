/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.List;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class SerializationUtilsTest {

    @Test
    void clonesASerializableMessageBatch() {
        final Batch source = new Batch("orders", List.of("one", "two"));

        final Batch restored = SerializationUtils.clone(source);

        assertThat(restored).isNotSameAs(source);
        assertThat(restored.topic).isEqualTo("orders");
        assertThat(restored.messages).containsExactly("one", "two");
    }

    public static final class Batch implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String topic;
        private final List<String> messages;

        public Batch(String topic, List<String> messages) {
            this.topic = topic;
            this.messages = messages;
        }
    }
}
