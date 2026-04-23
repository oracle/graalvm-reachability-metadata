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

public class JacksonEnumFactoryBasedDeserializerTest {

    @Test
    void enumDeserializerInvokesFactoryMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        FactoryEnum value = mapper.readValue("\"second\"", FactoryEnum.class);
        assertThat(value).isEqualTo(FactoryEnum.SECOND);
    }

    enum FactoryEnum {
        FIRST("first"),
        SECOND("second");

        private final String id;

        FactoryEnum(String id) {
            this.id = id;
        }

        @JsonCreator
        public static FactoryEnum fromId(String id) {
            for (FactoryEnum value : values()) {
                if (value.id.equals(id)) {
                    return value;
                }
            }
            throw new IllegalArgumentException(id);
        }
    }
}
