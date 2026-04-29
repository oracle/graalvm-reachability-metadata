/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.ClassData;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.FieldLayout;
import org.openjdk.jol.layouters.CurrentLayouter;

import java.util.SortedSet;

import static org.assertj.core.api.Assertions.assertThat;

public class CurrentLayouterTest {
    @Test
    void laysOutPrimitiveArrayUsingCurrentVmOffsets() {
        CurrentLayouter layouter = new CurrentLayouter();
        ClassData arrayData = ClassData.parseInstance(new int[] {1, 2, 3});

        ClassLayout layout = layouter.layout(arrayData);

        assertThat(arrayData.isArray()).isTrue();
        assertThat(arrayData.arrayLength()).isEqualTo(3);
        assertThat(layout.headerSize()).isPositive();
        assertThat(layout.instanceSize()).isGreaterThan(layout.headerSize());
        assertArrayLayoutFields(layout.fields());
        assertThat(layout.toString()).contains("[I.length", "[I.<elements>", "size =");
    }

    @Test
    void currentLayouterIdentifiesItself() {
        assertThat(new CurrentLayouter()).hasToString("Current VM Layout");
    }

    private static void assertArrayLayoutFields(SortedSet<FieldLayout> fields) {
        assertThat(fields).hasSize(2);
        assertThat(fields).extracting(FieldLayout::hostClass)
                .containsOnly("[I");
        assertThat(fields).extracting(FieldLayout::name)
                .containsExactly("length", "<elements>");
        assertThat(fields).extracting(FieldLayout::typeClass)
                .containsExactly("int", "int");
        for (FieldLayout field : fields) {
            assertThat(field.size()).isPositive();
        }
    }
}
