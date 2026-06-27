/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.annotation.JsonIgnore;
import shaded.parquet.com.fasterxml.jackson.annotation.JsonProperty;
import shaded.parquet.com.fasterxml.jackson.databind.JsonNode;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class AnnotatedMethodCollectorTest {
    @Test
    void objectMapperUsesBeanAndObjectMethodMixIns() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(MethodBean.class, MethodBeanMixin.class);
        mapper.addMixIn(Object.class, ObjectHashCodeMixin.class);

        final MethodBean original = new MethodBean("alpha", "secret");

        final JsonNode tree = mapper.readTree(mapper.writeValueAsString(original));

        assertThat(tree.get("external_name").asText()).isEqualTo("alpha");
        assertThat(tree.has("hidden")).isFalse();
        assertThat(tree.get("object_hash").asInt()).isEqualTo(original.hashCode());

        final MethodBean restored = mapper.readValue("{\"external_name\":\"beta\"}", MethodBean.class);

        assertThat(restored.getName()).isEqualTo("beta");
    }

    private abstract static class MethodBeanMixin {
        @JsonProperty("external_name")
        abstract String getName();

        @JsonProperty("external_name")
        abstract void setName(String name);

        @JsonIgnore
        abstract String getHidden();
    }

    private abstract static class ObjectHashCodeMixin {
        @JsonProperty("object_hash")
        public abstract int hashCode();
    }

    public static final class MethodBean {
        private String name;
        private String hidden;

        public MethodBean() {
        }

        MethodBean(String name, String hidden) {
            this.name = name;
            this.hidden = hidden;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHidden() {
            return hidden;
        }

        public void setHidden(String hidden) {
            this.hidden = hidden;
        }
    }
}
