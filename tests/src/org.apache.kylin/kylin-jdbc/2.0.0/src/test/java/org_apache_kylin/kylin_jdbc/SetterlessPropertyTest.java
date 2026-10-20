/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.util.ArrayList;
import java.util.List;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SetterlessPropertyTest {
    @Test
    void updatesACollectionExposedOnlyByAGetter() throws Exception {
        SetterlessBean bean = new ObjectMapper().readValue("{\"values\":[\"a\",\"b\"]}", SetterlessBean.class);

        assertThat(bean.getValues()).containsExactly("a", "b");
    }

    public static class SetterlessBean {
        private final List<String> values = new ArrayList<>();

        @JsonProperty("values")
        public List<String> getValues() {
            return values;
        }
    }
}
