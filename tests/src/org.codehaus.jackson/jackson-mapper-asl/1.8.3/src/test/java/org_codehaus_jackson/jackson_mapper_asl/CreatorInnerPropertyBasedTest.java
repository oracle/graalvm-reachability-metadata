/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CreatorInnerPropertyBasedTest {
    private static final String FULL_OBJECT_JSON = "{\"name\":\"property\",\"count\":3}";
    private static final String OBJECT_WITH_DEFAULTED_PRIMITIVE_JSON = "{\"name\":\"defaulted\"}";

    @Test
    void deserializesObjectThroughPropertyBasedConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ConstructorPropertyValue value = mapper.readValue(FULL_OBJECT_JSON, ConstructorPropertyValue.class);
        ConstructorPropertyValue defaultedValue = mapper.readValue(
                OBJECT_WITH_DEFAULTED_PRIMITIVE_JSON,
                ConstructorPropertyValue.class);

        assertThat(value.getCreatorKind()).isEqualTo("constructor");
        assertThat(value.getName()).isEqualTo("property");
        assertThat(value.getCount()).isEqualTo(3);
        assertThat(defaultedValue.getName()).isEqualTo("defaulted");
        assertThat(defaultedValue.getCount()).isEqualTo(0);
    }

    @Test
    void deserializesObjectThroughPropertyBasedFactoryMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        FactoryPropertyValue value = mapper.readValue(FULL_OBJECT_JSON, FactoryPropertyValue.class);
        FactoryPropertyValue defaultedValue = mapper.readValue(
                OBJECT_WITH_DEFAULTED_PRIMITIVE_JSON,
                FactoryPropertyValue.class);

        assertThat(value.getCreatorKind()).isEqualTo("factory");
        assertThat(value.getName()).isEqualTo("property");
        assertThat(value.getCount()).isEqualTo(3);
        assertThat(defaultedValue.getName()).isEqualTo("defaulted");
        assertThat(defaultedValue.getCount()).isEqualTo(0);
    }

    public static class ConstructorPropertyValue {
        private final String name;
        private final int count;
        private final String creatorKind;

        @JsonCreator
        public ConstructorPropertyValue(
                @JsonProperty("name") String name,
                @JsonProperty("count") int count) {
            this.name = name;
            this.count = count;
            this.creatorKind = "constructor";
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        public String getCreatorKind() {
            return creatorKind;
        }
    }

    public static class FactoryPropertyValue {
        private final String name;
        private final int count;
        private final String creatorKind;

        private FactoryPropertyValue(String name, int count, String creatorKind) {
            this.name = name;
            this.count = count;
            this.creatorKind = creatorKind;
        }

        @JsonCreator
        public static FactoryPropertyValue fromJson(
                @JsonProperty("name") String name,
                @JsonProperty("count") int count) {
            return new FactoryPropertyValue(name, count, "factory");
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        public String getCreatorKind() {
            return creatorKind;
        }
    }
}
