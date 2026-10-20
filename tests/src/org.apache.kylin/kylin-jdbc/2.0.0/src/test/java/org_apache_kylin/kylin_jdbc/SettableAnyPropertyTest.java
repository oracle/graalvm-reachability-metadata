/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonAnySetter;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SettableAnyPropertyTest {
    @Test
    void sendsUnknownPropertiesToTheAnySetter() throws Exception {
        FlexibleBean bean = new ObjectMapper().readValue("{\"region\":\"west\",\"count\":3}", FlexibleBean.class);

        assertThat(bean.attributes).containsEntry("region", "west").containsEntry("count", 3);
    }

    public static class FlexibleBean {
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        @JsonAnySetter
        public void add(String name, Object value) {
            attributes.put(name, value);
        }
    }
}
