/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.annotation.JsonFormat;
import shaded.parquet.com.fasterxml.jackson.annotation.JsonPropertyOrder;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import shaded.parquet.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

public class BeanAsArrayBuilderDeserializerTest {
    @Test
    void deserializesArrayShapedValueThroughBuilderBuildMethod() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();

        final ArrayBuiltBean bean = mapper.readValue(
                """
                [ "builder-array", 19 ]
                """,
                ArrayBuiltBean.class);

        assertThat(bean.name()).isEqualTo("builder-array");
        assertThat(bean.count()).isEqualTo(19);
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "name", "count" })
    @JsonDeserialize(builder = ArrayBuiltBeanBuilder.class)
    public static final class ArrayBuiltBean {
        private final String name;
        private final int count;

        private ArrayBuiltBean(String name, int count) {
            this.name = name;
            this.count = count;
        }

        String name() {
            return name;
        }

        int count() {
            return count;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "name", "count" })
    @JsonPOJOBuilder(withPrefix = "")
    public static final class ArrayBuiltBeanBuilder {
        public String name;
        public int count;

        public ArrayBuiltBean build() {
            return new ArrayBuiltBean(name, count);
        }
    }
}
