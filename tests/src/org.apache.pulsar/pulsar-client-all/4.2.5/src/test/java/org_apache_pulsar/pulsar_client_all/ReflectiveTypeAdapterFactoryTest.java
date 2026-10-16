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

public class ReflectiveTypeAdapterFactoryTest {
    @Test
    void bindsPojoFieldsForReadingAndWriting() {
        final Gson gson = new Gson();
        final Topic topic = gson.fromJson("{\"name\":\"events\",\"partitions\":4}", Topic.class);

        assertThat(topic.name).isEqualTo("events");
        assertThat(topic.partitions).isEqualTo(4);
        assertThat(gson.toJson(topic)).contains("\"name\":\"events\"", "\"partitions\":4");
    }

    public static final class Topic {
        private String name;
        private int partitions;
    }
}
