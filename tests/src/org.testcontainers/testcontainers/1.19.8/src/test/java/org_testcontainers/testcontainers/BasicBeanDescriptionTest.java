/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.BasicBeanDescription;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicBeanDescriptionTest {
    @Test
    void instantiatesAnIntrospectedBean() {
        ObjectMapper mapper = new ObjectMapper();
        BasicBeanDescription description = (BasicBeanDescription) mapper
            .getDeserializationConfig()
            .introspect(mapper.constructType(Bean.class));

        assertThat(description.instantiateBean(true)).isInstanceOf(Bean.class);
    }

    public static class Bean {
        public Bean() {}
    }
}
