/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CreatorInnerStringBasedTest {
    private static final String CONSTRUCTOR_JSON = "\"constructor-value\"";
    private static final String FACTORY_JSON = "\"factory-value\"";

    @Test
    void deserializesStringScalarWithStringConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ConstructorStringValue value = mapper.readValue(CONSTRUCTOR_JSON, ConstructorStringValue.class);

        assertThat(value.getCreatorKind()).isEqualTo("constructor");
        assertThat(value.getValue()).isEqualTo("constructor-value");
    }

    @Test
    void deserializesStringScalarWithStringFactoryMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        FactoryStringValue value = mapper.readValue(FACTORY_JSON, FactoryStringValue.class);

        assertThat(value.getCreatorKind()).isEqualTo("factory");
        assertThat(value.getValue()).isEqualTo("factory-value");
    }

    public static class ConstructorStringValue {
        private final String value;
        private final String creatorKind;

        @JsonCreator
        public ConstructorStringValue(String value) {
            this.value = value;
            this.creatorKind = "constructor";
        }

        public String getValue() {
            return value;
        }

        public String getCreatorKind() {
            return creatorKind;
        }
    }

    public static class FactoryStringValue {
        private final String value;
        private final String creatorKind;

        private FactoryStringValue(String value, String creatorKind) {
            this.value = value;
            this.creatorKind = creatorKind;
        }

        @JsonCreator
        public static FactoryStringValue fromJson(String value) {
            return new FactoryStringValue(value, "factory");
        }

        public String getValue() {
            return value;
        }

        public String getCreatorKind() {
            return creatorKind;
        }
    }
}
