/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.util.ArrayBuilders;

public class ArrayBuildersTest {
    @Test
    void insertInListNoDupMovesExistingElementToHeadInNewArrayOfSameComponentType() {
        final String first = "first";
        final String duplicate = "duplicate";
        final String last = "last";
        final String[] values = new String[] {first, duplicate, last};

        final String[] result = ArrayBuilders.insertInListNoDup(values, duplicate);

        assertThat(result).isNotSameAs(values);
        assertThat(result.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(result).containsExactly(duplicate, first, last);
        assertThat(values).containsExactly(first, duplicate, last);
    }

    @Test
    void insertInListNoDupPrependsMissingElementInNewArrayOfSameComponentType() {
        final String[] values = new String[] {"first", "second"};

        final String[] result = ArrayBuilders.insertInListNoDup(values, "new");

        assertThat(result).isNotSameAs(values);
        assertThat(result.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(result).containsExactly("new", "first", "second");
        assertThat(values).containsExactly("first", "second");
    }
}
