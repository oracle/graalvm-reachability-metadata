/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala3_library_3;

import org.junit.jupiter.api.Test;

import scala.runtime.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class ArraysTest {
    @Test
    void createsMultidimensionalArray() {
        String[][] array = Arrays.newArray(String.class, String[][].class, new int[]{2, 3});

        array[1][2] = "scala";

        assertThat(array.length).isEqualTo(2);
        assertThat(array[0].length).isEqualTo(3);
        assertThat(array[1].length).isEqualTo(3);
        assertThat(array[1][2]).isEqualTo("scala");
    }
}
