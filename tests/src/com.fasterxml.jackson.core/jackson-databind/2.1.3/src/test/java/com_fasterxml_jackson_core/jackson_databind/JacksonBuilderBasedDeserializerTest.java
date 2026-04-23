/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonBuilderBasedDeserializerTest {

    @Test
    void builderBasedDeserializerFinishesByInvokingBuildMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BuiltValue value = mapper.readValue("{\"value\":\"built\"}", BuiltValue.class);
        assertThat(value.value).isEqualTo("built");
    }

    @JsonDeserialize(builder = BuiltValueBuilder.class)
    static class BuiltValue {

        final String value;

        BuiltValue(String value) {
            this.value = value;
        }
    }

    public static class BuiltValueBuilder {

        private String value;

        public BuiltValueBuilder withValue(String value) {
            this.value = value;
            return this;
        }

        public BuiltValue build() {
            return new BuiltValue(value);
        }
    }
}
