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

/** Verifies ASM class hierarchy resolution used for frame computation. */
public class ClassWriterTest {
    @Test
    void resolvesCommonSuperclass() {
        ExposedClassWriter writer = new ExposedClassWriter();

        assertThat(writer.commonSuperclass("java/util/ArrayList", "java/util/LinkedList"))
                .isEqualTo("java/util/AbstractList");
    }

    private static final class ExposedClassWriter extends ClassWriter {
        private ExposedClassWriter() {
            super(COMPUTE_FRAMES);
        }

        private String commonSuperclass(String first, String second) {
            return getCommonSuperClass(first, second);
        }
    }
}
