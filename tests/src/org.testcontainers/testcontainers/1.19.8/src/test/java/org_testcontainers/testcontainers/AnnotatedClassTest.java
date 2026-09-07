/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.BeanDescription;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedClassTest {
    @Test
    void discoversDeclaredBeanMethods() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Bean.class, BeanMixin.class);
        BeanDescription description = mapper
            .getSerializationConfig()
            .introspect(mapper.constructType(Bean.class));

        assertThat(description.findProperties())
            .extracting(BeanPropertyDefinition::getName)
            .contains("value", "identity");
    }

    public static class Bean {
        public String getValue() {
            return "value";
        }
    }

    public abstract static class BeanMixin {
        @JsonProperty("identity")
        public abstract int hashCode();
    }
}
