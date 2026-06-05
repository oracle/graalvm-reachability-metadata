/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedCreatorCollectorTest {

    @Test
    void appliesMixInAnnotationsToStaticFactoryCreator() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(FactoryBackedValue.class, FactoryBackedValueMixIn.class);

        FactoryBackedValue value = mapper.readValue("\"from-json\"", FactoryBackedValue.class);

        assertThat(value.getValue()).isEqualTo("from-json");
    }

    public static final class FactoryBackedValue {
        private final String value;

        private FactoryBackedValue(String value) {
            this.value = value;
        }

        public static FactoryBackedValue create(String value) {
            return new FactoryBackedValue(value);
        }

        public String getValue() {
            return value;
        }
    }

    public abstract static class FactoryBackedValueMixIn {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static FactoryBackedValue create(String value) {
            return null;
        }
    }
}
