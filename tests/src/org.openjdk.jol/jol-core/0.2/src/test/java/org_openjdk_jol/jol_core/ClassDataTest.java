/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.ClassData;
import org.openjdk.jol.info.FieldData;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassDataTest {

    @Test
    void parsesDeclaredAndInheritedInstanceFields() {
        ClassData classData = ClassData.parseClass(SampleChild.class);

        Map<String, FieldData> fieldsByName = classData.fields().stream()
                .collect(Collectors.toMap(FieldData::name, field -> field));
        assertThat(classData.name()).isEqualTo(SampleChild.class.getCanonicalName());
        assertThat(classData.isArray()).isFalse();
        assertThat(classData.classHierarchy()).containsExactly("Object", "SampleParent", "SampleChild");
        assertThat(fieldsByName.keySet()).containsExactlyInAnyOrder("parentValue", "childValue");
        assertThat(fieldsByName.get("parentValue").hostClass()).isEqualTo("SampleParent");
        assertThat(fieldsByName.get("parentValue").typeClass()).isEqualTo("long");
        assertThat(fieldsByName.get("childValue").hostClass()).isEqualTo("SampleChild");
        assertThat(fieldsByName.get("childValue").typeClass()).isEqualTo("String");
        assertThat(classData.fieldsFor("SampleParent")).containsExactly(fieldsByName.get("parentValue"));
        assertThat(classData.fieldsFor("SampleChild")).containsExactly(fieldsByName.get("childValue"));
    }

    public static class SampleParent {
        private final long parentValue = 17L;
    }

    public static final class SampleChild extends SampleParent {
        private static final String STATIC_VALUE = "ignored";

        private final String childValue = "covered";
    }
}
