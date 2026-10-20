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

public class ReflectiveTypeAdapterFactoryInnerRecordAdapterTest {
    @Test
    void constructsRecordFromCollectedComponentValues() {
        Gson gson = new Gson();

        Message restored = gson.fromJson("{\"topic\":\"persistent://events\",\"partitions\":3}", Message.class);

        assertThat(restored).isEqualTo(new Message("persistent://events", 3));
        assertThat(gson.toJson(restored)).contains("\"partitions\":3");
    }

    public record Message(String topic, int partitions) {
    }
}
