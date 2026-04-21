/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_api;

import io.jsonwebtoken.lang.Objects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectsTest {

    @Test
    void addObjectToArrayInfersComponentTypeWhenSourceArrayIsNull() {
        String[] values = Objects.addObjectToArray(null, "jwt");

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).containsExactly("jwt");
    }

    @Test
    void toObjectArrayWrapsPrimitiveArrayValuesInTypedObjectArray() {
        Object[] values = Objects.toObjectArray(new int[]{1, 2, 3});

        assertThat(values).isInstanceOf(Integer[].class);
        assertThat(values).containsExactly(1, 2, 3);
    }
}
