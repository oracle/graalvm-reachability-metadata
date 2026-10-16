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

public class ReflectionHelperInnerRecordSupportedHelperTest {
    @Test
    void discoversCanonicalConstructorAndRecordAccessors() {
        final Gson gson = new Gson();
        final Topic topic = gson.fromJson("{\"name\":\"events\",\"partitions\":4}", Topic.class);

        assertThat(topic).isEqualTo(new Topic("events", 4));
        assertThat(gson.toJson(topic)).contains("\"name\":\"events\"", "\"partitions\":4");
    }

    public record Topic(String name, int partitions) {
    }
}
