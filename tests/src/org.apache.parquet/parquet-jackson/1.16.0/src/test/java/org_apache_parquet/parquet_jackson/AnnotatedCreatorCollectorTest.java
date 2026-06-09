/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.annotation.JsonCreator;
import shaded.parquet.com.fasterxml.jackson.annotation.JsonProperty;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class AnnotatedCreatorCollectorTest {
    @Test
    void mixInAnnotationsSelectStaticFactoryCreator() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(FactoryCreatedBean.class, FactoryCreatedBeanMixin.class);

        final FactoryCreatedBean value = mapper.readValue(
                "{\"creator_name\":\"alpha\"}", FactoryCreatedBean.class);

        assertThat(value.name()).isEqualTo("alpha");
    }

    private abstract static class FactoryCreatedBeanMixin {
        @JsonCreator
        public static FactoryCreatedBean fromJson(@JsonProperty("creator_name") String name) {
            return null;
        }
    }

    public static final class FactoryCreatedBean {
        private final String name;

        private FactoryCreatedBean(String name) {
            this.name = name;
        }

        public static FactoryCreatedBean fromJson(String name) {
            return new FactoryCreatedBean(name);
        }

        public String name() {
            return name;
        }
    }
}
