/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala3_library_3;

import org.junit.jupiter.api.Test;

import scala.runtime.LazyVals;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyValsTest {
    @Test
    void resolvesAndReadsInstanceField() {
        LazyValsOffsetTarget target = new LazyValsOffsetTarget();

        long offset = LazyVals.getOffset(LazyValsOffsetTarget.class, "bitmap");

        assertThat(offset).isNotNegative();
        assertThat(LazyVals.get(target, offset)).isEqualTo(42L);
    }

    public static final class LazyValsOffsetTarget {
        private volatile long bitmap = 42L;
    }
}
