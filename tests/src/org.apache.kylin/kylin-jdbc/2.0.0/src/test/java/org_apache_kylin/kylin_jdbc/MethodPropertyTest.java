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

public class MethodPropertyTest {
    @Test
    void assignsAJsonValueThroughASetter() throws Exception {
        MethodBean bean = new ObjectMapper().readValue("{\"enabled\":true}", MethodBean.class);

        assertThat(bean.isEnabled()).isTrue();
    }

    @Test
    void assignsAJsonValueThroughABuilderMethod() throws Exception {
        BuiltMethodBean bean = new ObjectMapper().readValue("{\"enabled\":true}", BuiltMethodBean.class);

        assertThat(bean.enabled).isTrue();
    }

    @JsonDeserialize(builder = MethodBuilder.class)
    public static class BuiltMethodBean {
        private final boolean enabled;

        private BuiltMethodBean(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @JsonPOJOBuilder(withPrefix = "with")
    public static class MethodBuilder {
        private boolean enabled;

        public MethodBuilder withEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public BuiltMethodBean build() {
            return new BuiltMethodBean(enabled);
        }
    }

    public static class MethodBean {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
