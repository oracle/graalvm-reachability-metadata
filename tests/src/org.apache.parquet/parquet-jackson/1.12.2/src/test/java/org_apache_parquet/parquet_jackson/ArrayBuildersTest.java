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
    void insertInListNoDupMovesExistingElementToFrontUsingSameArrayComponentType() {
        NamedValue first = new NamedValue("first");
        NamedValue duplicate = new NamedValue("duplicate");
        NamedValue third = new NamedValue("third");
        NamedValue[] originalValues = new NamedValue[] {first, duplicate, third};

        NamedValue[] reorderedValues = ArrayBuilders.insertInListNoDup(originalValues, duplicate);

        assertThat(reorderedValues).isNotSameAs(originalValues);
        assertThat(reorderedValues.getClass()).isEqualTo(NamedValue[].class);
        assertThat(reorderedValues).containsExactly(duplicate, first, third);
    }

    @Test
    void insertInListNoDupPrependsNewElementUsingSameArrayComponentType() {
        NamedValue first = new NamedValue("first");
        NamedValue second = new NamedValue("second");
        NamedValue inserted = new NamedValue("inserted");
        NamedValue[] originalValues = new NamedValue[] {first, second};

        NamedValue[] expandedValues = ArrayBuilders.insertInListNoDup(originalValues, inserted);

        assertThat(expandedValues).isNotSameAs(originalValues);
        assertThat(expandedValues.getClass()).isEqualTo(NamedValue[].class);
        assertThat(expandedValues).containsExactly(inserted, first, second);
    }

    private static final class NamedValue {
        private final String name;

        private NamedValue(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
