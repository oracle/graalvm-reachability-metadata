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
import static org.assertj.core.api.Assertions.tuple;

public class ClassDataTest {
    @Test
    void parsesInstanceFieldsDeclaredAcrossClassHierarchy() {
        ClassData classData = ClassData.parseClass(ParsedLayoutTarget.class);

        assertThat(classData.name()).endsWith("ClassDataTest.ParsedLayoutTarget");
        assertThat(classData.isArray()).isFalse();
        assertThat(classData.classHierarchy())
                .contains("Object", "BaseLayoutTarget", "ParsedLayoutTarget");
        assertThat(classData.fields())
                .extracting(FieldData::hostClass, FieldData::name, FieldData::typeClass)
                .contains(
                        tuple("ParsedLayoutTarget", "label", "String"),
                        tuple("ParsedLayoutTarget", "count", "int"),
                        tuple("BaseLayoutTarget", "inheritedValue", "long"))
                .doesNotContain(tuple("ParsedLayoutTarget", "STATIC_LABEL", "String"));
        assertThat(classData.fieldsFor("ParsedLayoutTarget"))
                .extracting(FieldData::name)
                .containsExactlyInAnyOrder("label", "count");
        assertThat(classData.fieldsFor("BaseLayoutTarget"))
                .extracting(FieldData::name)
                .containsExactly("inheritedValue");
    }

    private static class BaseLayoutTarget {
        private final long inheritedValue = 123L;
    }

    private static class ParsedLayoutTarget extends BaseLayoutTarget {
        private static final String STATIC_LABEL = "ignored";

        private final String label = "layout";
        private final int count = 7;
    }
}
