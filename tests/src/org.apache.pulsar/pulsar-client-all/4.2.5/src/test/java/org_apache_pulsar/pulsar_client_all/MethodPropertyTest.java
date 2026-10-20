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

public class MethodPropertyTest {
    @Test
    void deserializesAndUpdatesSetterBackedProperties() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SetterBean created = mapper.readValue("{\"name\":\"created\",\"count\":3}", SetterBean.class);
        SetterBean updated = new SetterBean();

        mapper.readerForUpdating(updated).readValue("{\"name\":\"updated\",\"count\":5}");

        assertThat(created.getName()).isEqualTo("created");
        assertThat(created.getCount()).isEqualTo(3);
        assertThat(updated.getName()).isEqualTo("updated");
        assertThat(updated.getCount()).isEqualTo(5);
    }

    public static class SetterBean {
        private String name;
        private int count;

        public SetterBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
