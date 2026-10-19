/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class SetterlessPropertyTest {
    @Test
    void populatesCollectionReturnedByGetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        SetterlessBean bean = mapper.readValue("{\"values\":[\"one\",\"two\"]}", SetterlessBean.class);

        assertThat(bean.getValues()).containsExactly("one", "two");
    }

    public static class SetterlessBean {
        private final List<String> values = new ArrayList<>();

        public List<String> getValues() {
            return values;
        }
    }
}
