/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_owlike.genson;

import static org.assertj.core.api.Assertions.assertThat;

import com.owlike.genson.internal.asm.ClassWriter;
import org.junit.jupiter.api.Test;

public class ClassWriterTest {
    @Test
    void resolvesCommonSuperclassUsingWritersClassLoader() {
        HierarchyResolvingClassWriter writer = new HierarchyResolvingClassWriter();

        assertThat(writer.commonSuperClass("java/util/ArrayList", "java/util/LinkedList"))
                .isEqualTo("java/util/AbstractList");
    }

    private static final class HierarchyResolvingClassWriter extends ClassWriter {
        private HierarchyResolvingClassWriter() {
            super(0);
        }

        private String commonSuperClass(String firstType, String secondType) {
            return getCommonSuperClass(firstType, secondType);
        }
    }
}
