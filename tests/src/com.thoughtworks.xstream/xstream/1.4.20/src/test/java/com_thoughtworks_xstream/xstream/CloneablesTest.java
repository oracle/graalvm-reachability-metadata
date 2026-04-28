/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.core.util.Cloneables;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CloneablesTest {
    @Test
    void clonesPrimitiveArrayUsingIndependentArrayInstance() {
        int[] original = new int[]{1, 1, 2, 3, 5, 8};

        Object cloned = Cloneables.clone(original);

        assertThat(cloned).isInstanceOf(int[].class);
        assertThat((int[])cloned).containsExactly(original);
        assertThat(cloned).isNotSameAs(original);
    }
}
