/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_asm9_shaded;

import org.apache.xbean.asm9.ClassWriter;
import org.apache.xbean.asm9.Type;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassWriterTest {
    @Test
    void returnsSharedAbstractSuperclassForConcreteTypes() {
        TestableClassWriter classWriter = new TestableClassWriter();

        String commonSuperClass = classWriter.commonSuperClass(
                Type.getInternalName(FirstChild.class),
                Type.getInternalName(SecondChild.class));

        assertThat(commonSuperClass).isEqualTo(Type.getInternalName(Parent.class));
    }

    @Test
    void returnsObjectWhenEitherLoadedTypeIsAnInterface() {
        TestableClassWriter classWriter = new TestableClassWriter();

        String commonSuperClass = classWriter.commonSuperClass("java/util/List", "java/util/Set");

        assertThat(commonSuperClass).isEqualTo("java/lang/Object");
    }

    private abstract static class Parent {
    }

    private static final class FirstChild extends Parent {
    }

    private static final class SecondChild extends Parent {
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
