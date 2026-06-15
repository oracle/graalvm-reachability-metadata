/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class EnumDeserializerInnerFactoryBasedDeserializerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void deserializesEnumWithJsonCreatorFactoryMethod() throws Exception {
        FactoryBackedEnum value = MAPPER.readValue("\"external-id\"", FactoryBackedEnum.class);

        assertThat(value).isEqualTo(FactoryBackedEnum.MATCHED_BY_FACTORY);
    }

    public enum FactoryBackedEnum {
        MATCHED_BY_FACTORY("external-id"),
        OTHER("other-id");

        private final String externalId;

        FactoryBackedEnum(String externalId) {
            this.externalId = externalId;
        }

        @JsonCreator
        public static FactoryBackedEnum fromJson(String externalId) {
            for (FactoryBackedEnum value : values()) {
                if (value.externalId.equals(externalId)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown enum id: " + externalId);
        }
    }
}
