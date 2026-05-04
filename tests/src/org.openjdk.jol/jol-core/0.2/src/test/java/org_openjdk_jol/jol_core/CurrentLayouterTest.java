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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CurrentLayouterTest {
    @Test
    void laysOutPrimitiveArrayUsingCurrentVmArrayMetadata() {
        int[] values = new int[] {1, 2, 3};
        ClassData classData = ClassData.parseInstance(values);

        ClassLayout layout = new CurrentLayouter().layout(classData);

        List<FieldLayout> fields = new ArrayList<FieldLayout>(layout.fields());
        assertThat(fields).hasSize(2);

        FieldLayout length = fields.get(0);
        assertThat(length.hostClass()).isEqualTo(int[].class.getName());
        assertThat(length.name()).isEqualTo("length");
        assertThat(length.typeClass()).isEqualTo("int");
        assertThat(length.offset()).isEqualTo(layout.headerSize());
        assertThat(length.size()).isEqualTo(Integer.BYTES);

        FieldLayout elements = fields.get(1);
        assertThat(elements.hostClass()).isEqualTo(int[].class.getName());
        assertThat(elements.name()).isEqualTo("<elements>");
        assertThat(elements.typeClass()).isEqualTo("int");
        assertThat(elements.offset()).isGreaterThan(length.offset());
        assertThat(elements.size()).isEqualTo(Integer.BYTES * values.length);
        assertThat(layout.instanceSize()).isGreaterThanOrEqualTo(elements.offset() + elements.size());
    }
}
