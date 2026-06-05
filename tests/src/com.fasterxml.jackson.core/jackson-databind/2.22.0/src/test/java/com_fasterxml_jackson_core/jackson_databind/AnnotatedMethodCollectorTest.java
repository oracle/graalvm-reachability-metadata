/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedMethodCollectorTest {

    @Test
    void appliesDeclaredMethodAnnotationsFromClassMixIn() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(MixInTarget.class, RenamingMixIn.class);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(new MixInTarget("value")));

        assertThat(json.has("name")).isFalse();
        assertThat(json.get("renamedName").textValue()).isEqualTo("value");
    }

    @Test
    void exposesObjectHashCodeThroughObjectMixIn() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Object.class, ObjectHashCodeMixIn.class);
        HashCodeTarget target = new HashCodeTarget();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(target));

        assertThat(json.get("objectHash").intValue()).isEqualTo(target.hashCode());
    }

    public static final class MixInTarget {
        private final String name;

        public MixInTarget(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public abstract static class RenamingMixIn {
        @JsonProperty("renamedName")
        public abstract String getName();
    }

    public static final class HashCodeTarget {
        @Override
        public int hashCode() {
            return 1138;
        }
    }

    public abstract static class ObjectHashCodeMixIn {
        @JsonProperty("objectHash")
        public abstract int hashCode();
    }
}
