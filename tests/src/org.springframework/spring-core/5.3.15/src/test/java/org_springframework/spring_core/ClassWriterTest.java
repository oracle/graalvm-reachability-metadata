/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassWriter;

/** Verifies ASM's default common-superclass calculation. */
public class ClassWriterTest {
    @Test
    void resolvesCommonSuperclassByLoadingBothTypes() {
        ExposedClassWriter writer = new ExposedClassWriter();

        String commonType = writer.commonSuperClass("java/util/ArrayList", "java/util/LinkedList");

        assertThat(commonType).isEqualTo("java/util/AbstractList");
    }

    private static final class ExposedClassWriter extends ClassWriter {
        private ExposedClassWriter() {
            super(0);
        }

        private String commonSuperClass(String first, String second) {
            return getCommonSuperClass(first, second);
        }
    }
}
