/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.util.ObjectUtils;

public class ObjectUtilsTest {

    @Test
    void convertsPrimitiveArrayToWrapperObjectArray() {
        Object[] objectArray = ObjectUtils.toObjectArray(new int[] {1, 2, 3});

        assertThat(objectArray)
                .isInstanceOf(Integer[].class)
                .containsExactly(1, 2, 3);
    }

    @Test
    void returnsExistingObjectArrayUnchanged() {
        String[] sourceArray = new String[] {"alpha", "bravo"};

        Object[] objectArray = ObjectUtils.toObjectArray(sourceArray);

        assertThat(objectArray).isSameAs(sourceArray);
    }

    @Test
    void handlesNullAndEmptyPrimitiveArraysAsEmptyObjectArrays() {
        assertThat(ObjectUtils.toObjectArray(null)).isEmpty();
        assertThat(ObjectUtils.toObjectArray(new long[0])).isEmpty();
    }

    @Test
    void rejectsNonArraySource() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ObjectUtils.toObjectArray("not an array"))
                .withMessageContaining("Source is not an array");
    }
}
