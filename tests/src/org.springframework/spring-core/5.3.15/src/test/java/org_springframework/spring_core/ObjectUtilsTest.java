/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.util.ObjectUtils;

/** Verifies reflective array creation for object and primitive arrays. */
public class ObjectUtilsTest {
    @Test
    void appendsObjectsAndBoxesPrimitiveArrays() {
        String[] values = ObjectUtils.addObjectToArray(new String[] {"spring"}, "core");
        Object[] boxed = ObjectUtils.toObjectArray(new int[] {5, 3, 15});

        assertThat(values).containsExactly("spring", "core");
        assertThat(boxed).containsExactly(5, 3, 15);
    }
}
