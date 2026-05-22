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

import static org.assertj.core.api.Assertions.assertThat;

public class CurrentLayouterTest {
    @Test
    void laysOutPrimitiveArrayUsingCurrentVmArrayClass() {
        ClassData data = ClassData.parseInstance(new int[] {1, 2, 3});

        ClassLayout layout = new CurrentLayouter().layout(data);

        assertThat(layout.headerSize()).isPositive();
        assertThat(layout.instanceSize()).isGreaterThan(layout.headerSize());
        assertThat(layout.fields())
                .extracting(FieldLayout::name)
                .containsExactly("length", "<elements>");
        assertThat(layout.fields())
                .extracting(FieldLayout::hostClass)
                .containsOnly(int[].class.getName());
        assertThat(layout.fields())
                .filteredOn(field -> "<elements>".equals(field.name()))
                .singleElement()
                .satisfies(field -> {
                    assertThat(field.typeClass()).isEqualTo("int");
                    assertThat(field.size()).isEqualTo(Integer.BYTES * 3);
                });
    }
}
