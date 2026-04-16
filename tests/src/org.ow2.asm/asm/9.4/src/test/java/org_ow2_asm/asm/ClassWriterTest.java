/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;

import static org.assertj.core.api.Assertions.assertThat;

class ClassWriterTest {
    @Test
    void returnsSharedAbstractSuperclassForConcreteCollectionTypes() {
        TestableClassWriter classWriter = new TestableClassWriter();

        String commonSuperClass =
                classWriter.commonSuperClass("java/util/ArrayList", "java/util/LinkedList");

        assertThat(commonSuperClass).isEqualTo("java/util/AbstractList");
    }

    @Test
    void returnsObjectWhenEitherLoadedTypeIsAnInterface() {
        TestableClassWriter classWriter = new TestableClassWriter();

        String commonSuperClass = classWriter.commonSuperClass("java/util/List", "java/util/Set");

        assertThat(commonSuperClass).isEqualTo("java/lang/Object");
    }

    private static final class TestableClassWriter extends ClassWriter {
        private TestableClassWriter() {
            super(0);
        }

        private String commonSuperClass(String firstType, String secondType) {
            return super.getCommonSuperClass(firstType, secondType);
        }
    }
}
