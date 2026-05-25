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

import static org.assertj.core.api.Assertions.assertThat;

public class ClassDataTest {
    @Test
    void parsesDeclaredInstanceFieldsAcrossClassHierarchy() {
        ClassData data = ClassData.parseClass(DataChild.class);

        assertThat(data.name()).isEqualTo(DataChild.class.getCanonicalName());
        assertThat(data.isArray()).isFalse();
        assertThat(data.classHierarchy()).containsExactly("Object", "DataParent", "DataChild");
        assertThat(data.fields())
                .extracting(FieldData::name)
                .containsExactlyInAnyOrder("parentValue", "childValue");
        assertThat(data.fieldsFor("DataParent"))
                .singleElement()
                .satisfies(field -> {
                    assertThat(field.name()).isEqualTo("parentValue");
                    assertThat(field.typeClass()).isEqualTo("int");
                    assertThat(field.vmOffset()).isGreaterThanOrEqualTo(0);
                });
        assertThat(data.fieldsFor("DataChild"))
                .singleElement()
                .satisfies(field -> {
                    assertThat(field.name()).isEqualTo("childValue");
                    assertThat(field.typeClass()).isEqualTo("String");
                    assertThat(field.vmOffset()).isGreaterThanOrEqualTo(0);
                });
    }

    static class DataParent {
        private int parentValue;
    }

    static class DataChild extends DataParent {
        private static String ignoredStatic = "ignored";
        private String childValue;
    }
}
