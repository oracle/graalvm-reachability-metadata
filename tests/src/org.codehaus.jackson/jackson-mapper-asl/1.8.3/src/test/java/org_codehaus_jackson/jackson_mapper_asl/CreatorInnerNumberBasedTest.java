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

public class CreatorInnerNumberBasedTest {
    private static final String INT_JSON = "27";
    private static final String LONG_JSON = "3000000000";

    @Test
    void deserializesIntegerAndLongScalarsWithNumericConstructors() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ConstructorNumberValue intValue = mapper.readValue(INT_JSON, ConstructorNumberValue.class);
        ConstructorNumberValue longValue = mapper.readValue(LONG_JSON, ConstructorNumberValue.class);

        assertThat(intValue.getCreatorKind()).isEqualTo("int-constructor");
        assertThat(intValue.getValue()).isEqualTo(27L);
        assertThat(longValue.getCreatorKind()).isEqualTo("long-constructor");
        assertThat(longValue.getValue()).isEqualTo(3000000000L);
    }

    @Test
    void deserializesIntegerAndLongScalarsWithNumericFactoryMethods() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        FactoryNumberValue intValue = mapper.readValue(INT_JSON, FactoryNumberValue.class);
        FactoryNumberValue longValue = mapper.readValue(LONG_JSON, FactoryNumberValue.class);

        assertThat(intValue.getCreatorKind()).isEqualTo("int-factory");
        assertThat(intValue.getValue()).isEqualTo(27L);
        assertThat(longValue.getCreatorKind()).isEqualTo("long-factory");
        assertThat(longValue.getValue()).isEqualTo(3000000000L);
    }

    public static class ConstructorNumberValue {
        private final long value;
        private final String creatorKind;

        @JsonCreator
        public ConstructorNumberValue(int value) {
            this.value = value;
            this.creatorKind = "int-constructor";
        }

        @JsonCreator
        public ConstructorNumberValue(long value) {
            this.value = value;
            this.creatorKind = "long-constructor";
        }

        public long getValue() {
            return value;
        }

        public String getCreatorKind() {
            return creatorKind;
        }
    }

    public static class FactoryNumberValue {
        private final long value;
        private final String creatorKind;

        private FactoryNumberValue(long value, String creatorKind) {
            this.value = value;
            this.creatorKind = creatorKind;
        }

        @JsonCreator
        public static FactoryNumberValue fromJson(int value) {
            return new FactoryNumberValue(value, "int-factory");
        }

        @JsonCreator
        public static FactoryNumberValue fromJson(long value) {
            return new FactoryNumberValue(value, "long-factory");
        }

        public long getValue() {
            return value;
        }

        public String getCreatorKind() {
            return creatorKind;
        }
    }
}
