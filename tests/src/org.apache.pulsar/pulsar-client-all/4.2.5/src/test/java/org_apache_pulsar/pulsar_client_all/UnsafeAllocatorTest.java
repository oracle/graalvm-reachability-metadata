/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class UnsafeAllocatorTest {

    @Test
    void constructsAndPopulatesAClassWithoutANoArgumentConstructor() {
        final ConstructorOnlyMessage restored =
                new Gson().fromJson("{\"topic\":\"orders\",\"sequence\":7}", ConstructorOnlyMessage.class);

        assertThat(restored.topic).isEqualTo("orders");
        assertThat(restored.sequence).isEqualTo(7);
    }

    public static final class ConstructorOnlyMessage {
        private String topic;
        private int sequence;

        public ConstructorOnlyMessage(String topic, int sequence) {
            this.topic = topic;
            this.sequence = sequence;
        }
    }
}
