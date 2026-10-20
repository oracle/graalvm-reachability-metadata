/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumDeserializerInnerFactoryBasedDeserializerTest {
    @Test
    void createsAnEnumThroughItsJsonFactory() throws Exception {
        Priority priority = new ObjectMapper().readValue("\"urgent\"", Priority.class);

        assertThat(priority).isEqualTo(Priority.HIGH);
    }

    public enum Priority {
        LOW,
        HIGH;

        @JsonCreator
        public static Priority fromJson(String value) {
            return "urgent".equals(value) ? HIGH : LOW;
        }
    }
}
