/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.type.ArrayType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonArrayTypeTest {

    @Test
    void arrayTypeConstructCreatesTypedArrayClass() {
        ArrayType arrayType = ArrayType.construct(TypeFactory.defaultInstance().constructType(String.class), null, null);
        assertThat(arrayType.getRawClass()).isEqualTo(String[].class);
    }
}
