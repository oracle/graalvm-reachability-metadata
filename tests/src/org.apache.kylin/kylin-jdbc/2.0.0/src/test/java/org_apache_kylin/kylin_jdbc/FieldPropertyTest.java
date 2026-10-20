/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldPropertyTest {
    @Test
    void assignsAJsonValueToAPublicField() throws Exception {
        FieldBean bean = new ObjectMapper().readValue("{\"quantity\":12}", FieldBean.class);

        assertThat(bean.quantity).isEqualTo(12);
    }

    @Test
    void assignsAJsonValueToABuilderField() throws Exception {
        BuiltFieldBean bean = new ObjectMapper().readValue("{\"quantity\":15}", BuiltFieldBean.class);

        assertThat(bean.quantity).isEqualTo(15);
    }

    @JsonDeserialize(builder = FieldBuilder.class)
    public static class BuiltFieldBean {
        private final int quantity;

        private BuiltFieldBean(int quantity) {
            this.quantity = quantity;
        }
    }

    @JsonPOJOBuilder(withPrefix = "with")
    public static class FieldBuilder {
        public int quantity;

        public BuiltFieldBean build() {
            return new BuiltFieldBean(quantity);
        }
    }

    public static class FieldBean {
        public int quantity;
    }
}
