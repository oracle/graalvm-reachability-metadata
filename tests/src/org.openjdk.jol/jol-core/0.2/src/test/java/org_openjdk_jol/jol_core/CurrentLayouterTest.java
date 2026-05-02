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

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class CurrentLayouterTest {

    @Test
    void laysOutPrimitiveArraysWithCurrentVmArrayOffsets() {
        ClassData arrayData = ClassData.parseInstance(new int[] {1, 2, 3});
        CurrentLayouter layouter = new CurrentLayouter();

        ClassLayout layout = layouter.layout(arrayData);

        Map<String, FieldLayout> fieldsByName = fieldsByName(layout);
        assertThat(fieldsByName.keySet()).containsExactlyInAnyOrder("length", "<elements>");
        assertThat(fieldsByName.get("length").hostClass()).isEqualTo(int[].class.getName());
        assertThat(fieldsByName.get("length").typeClass()).isEqualTo("int");
        assertThat(fieldsByName.get("length").size()).isEqualTo(Integer.BYTES);
        assertThat(fieldsByName.get("<elements>").hostClass()).isEqualTo(int[].class.getName());
        assertThat(fieldsByName.get("<elements>").typeClass()).isEqualTo("int");
        assertThat(fieldsByName.get("<elements>").size()).isEqualTo(3 * Integer.BYTES);
        assertThat(fieldsByName.get("<elements>").offset()).isGreaterThan(fieldsByName.get("length").offset());
        assertThat(layout.instanceSize()).isGreaterThanOrEqualTo(
                fieldsByName.get("<elements>").offset() + fieldsByName.get("<elements>").size());
    }

    @Test
    void laysOutReferenceArraysWithCurrentVmArrayOffsets() {
        ClassData arrayData = ClassData.parseInstance(new String[] {"alpha", "beta"});
        CurrentLayouter layouter = new CurrentLayouter();

        ClassLayout layout = layouter.layout(arrayData);

        Map<String, FieldLayout> fieldsByName = fieldsByName(layout);
        assertThat(fieldsByName.keySet()).containsExactlyInAnyOrder("length", "<elements>");
        assertThat(fieldsByName.get("length").hostClass()).isEqualTo(String[].class.getName());
        assertThat(fieldsByName.get("length").typeClass()).isEqualTo("int");
        assertThat(fieldsByName.get("length").size()).isEqualTo(Integer.BYTES);
        assertThat(fieldsByName.get("<elements>").hostClass()).isEqualTo(String[].class.getName());
        assertThat(fieldsByName.get("<elements>").typeClass()).isEqualTo(String.class.getName());
        assertThat(fieldsByName.get("<elements>").size()).isPositive();
        assertThat(fieldsByName.get("<elements>").offset()).isGreaterThan(fieldsByName.get("length").offset());
        assertThat(layout.instanceSize()).isGreaterThanOrEqualTo(
                fieldsByName.get("<elements>").offset() + fieldsByName.get("<elements>").size());
    }

    private static Map<String, FieldLayout> fieldsByName(ClassLayout layout) {
        return layout.fields().stream().collect(Collectors.toMap(FieldLayout::name, field -> field));
    }
}
