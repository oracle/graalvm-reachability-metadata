/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassWriter;
import org.junit.jupiter.api.Test;

public class ClassWriterTest {
    @Test
    void resolvesTheSharedSuperclassOfConcreteCollectionTypes() {
        ExposedClassWriter writer = new ExposedClassWriter();

        String commonSuperClass =
                writer.commonSuperClass("java/util/ArrayList", "java/util/LinkedList");

        assertThat(commonSuperClass).isEqualTo("java/util/AbstractList");
    }

    private static final class ExposedClassWriter extends ClassWriter {
        private ExposedClassWriter() {
            super(0);
        }

        private String commonSuperClass(String firstType, String secondType) {
            return super.getCommonSuperClass(firstType, secondType);
        }
    }
}
