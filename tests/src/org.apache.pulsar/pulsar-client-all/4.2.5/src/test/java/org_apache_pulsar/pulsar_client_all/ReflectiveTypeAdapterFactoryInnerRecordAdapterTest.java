/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

public class ReflectiveTypeAdapterFactoryInnerRecordAdapterTest {
    @Test
    void createsRecordsFromAccumulatedComponentValues() {
        final Coordinate value = new GsonBuilder().create().fromJson(
                "{\"group\":\"org.apache.pulsar\",\"artifact\":\"pulsar-client-all\"}", Coordinate.class);

        assertThat(value.group()).isEqualTo("org.apache.pulsar");
        assertThat(value.artifact()).isEqualTo("pulsar-client-all");
    }

    public record Coordinate(String group, String artifact) {
    }
}
