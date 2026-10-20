/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class FieldPropertyTest {
    @Test
    void deserializesAndUpdatesFieldBackedProperties() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        FieldBean created = mapper.readValue("{\"name\":\"created\",\"count\":3}", FieldBean.class);
        FieldBean updated = new FieldBean();

        mapper.readerForUpdating(updated).readValue("{\"name\":\"updated\",\"count\":5}");

        assertThat(created.name).isEqualTo("created");
        assertThat(created.count).isEqualTo(3);
        assertThat(updated.name).isEqualTo("updated");
        assertThat(updated.count).isEqualTo(5);
    }

    public static class FieldBean {
        public String name;
        public int count;

        public FieldBean() {
        }
    }
}
