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
import shaded.parquet.com.fasterxml.jackson.databind.BeanDescription;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.introspect.AnnotatedMethod;

public class AnnotatedMethodCollectorTest {
    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];

    @Test
    void appliesClassMixInMethodAnnotationsDuringSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(CollectorTarget.class, CollectorTargetMixIn.class);

        String json = mapper.writeValueAsString(new CollectorTarget("visible", "hidden"));

        assertThat(json).contains("\"displayName\":\"visible\"");
        assertThat(json).doesNotContain("name", "secret");
    }

    @Test
    void includesObjectMethodMixInDuringMethodCollection() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Object.class, ObjectMethodMixIn.class);

        BeanDescription description = mapper.getSerializationConfig().introspect(mapper.constructType(CollectorTarget.class));
        AnnotatedMethod hashCodeMethod = description.findMethod("hashCode", NO_PARAMETERS);

        assertThat(hashCodeMethod).isNotNull();
        assertThat(hashCodeMethod.getName()).isEqualTo("hashCode");
    }

    public static final class CollectorTarget {
        private final String name;
        private final String secret;

        public CollectorTarget(String name, String secret) {
            this.name = name;
            this.secret = secret;
        }

        public String getName() {
            return name;
        }

        public String getSecret() {
            return secret;
        }
    }

    abstract static class CollectorTargetMixIn {
        @JsonProperty("displayName")
        abstract String getName();

        @JsonIgnore
        abstract String getSecret();
    }

    abstract static class ObjectMethodMixIn {
        @JsonProperty("objectHash")
        public abstract int hashCode();
    }
}
