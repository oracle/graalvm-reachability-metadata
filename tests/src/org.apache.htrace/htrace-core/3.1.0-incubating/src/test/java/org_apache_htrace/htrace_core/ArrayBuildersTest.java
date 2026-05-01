/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.databind.util.ArrayBuilders;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayBuildersTest {
    @Test
    void insertInListPrependsElementToNewArrayOfSameComponentType() {
        String[] input = {"second", "third"};
        String first = "first";

        String[] result = ArrayBuilders.insertInList(input, first);

        assertThat(result).containsExactly(first, "second", "third");
        assertThat(result).isNotSameAs(input);
        assertThat(result.getClass()).isSameAs(input.getClass());
    }

    @Test
    void insertInListNoDupMovesExistingDuplicateToFront() {
        String first = "first";
        String duplicate = new String("duplicate");
        String last = "last";
        String[] input = {first, duplicate, last};

        String[] result = ArrayBuilders.insertInListNoDup(input, duplicate);

        assertThat(result).containsExactly(duplicate, first, last);
        assertThat(result).isNotSameAs(input);
        assertThat(result.getClass()).isSameAs(input.getClass());
    }

    @Test
    void insertInListNoDupPrependsMissingElement() {
        String[] input = {"second", "third"};
        String first = "first";

        String[] result = ArrayBuilders.insertInListNoDup(input, first);

        assertThat(result).containsExactly(first, "second", "third");
        assertThat(result).isNotSameAs(input);
        assertThat(result.getClass()).isSameAs(input.getClass());
    }
}
