/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ObjectArrays;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectArraysTest {
    @Test
    void createsAndConcatenatesTypedArrays() {
        assertThat(ObjectArrays.newArray(String.class, 2)).hasSize(2).containsOnlyNulls();
        assertThat(ObjectArrays.concat(new String[] { "a" }, new String[] { "b" }, String.class))
            .containsExactly("a", "b");
    }
}
