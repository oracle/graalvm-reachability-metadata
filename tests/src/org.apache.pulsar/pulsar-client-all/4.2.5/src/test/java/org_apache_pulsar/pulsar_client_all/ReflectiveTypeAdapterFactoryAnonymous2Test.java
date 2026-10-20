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

public class ReflectiveTypeAdapterFactoryAnonymous2Test {
    @Test
    void readsAndWritesPrivateFieldsThroughBoundFields() {
        Gson gson = new Gson();
        PrivateFields input = new PrivateFields("client", 4);

        String json = gson.toJson(input);
        PrivateFields restored = gson.fromJson("{\"name\":\"admin\",\"count\":7}", PrivateFields.class);

        assertThat(json).contains("\"name\":\"client\"").contains("\"count\":4");
        assertThat(restored.name).isEqualTo("admin");
        assertThat(restored.count).isEqualTo(7);
    }

    public static class PrivateFields {
        private String name;
        private int count;

        public PrivateFields() {
        }

        public PrivateFields(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }
}
