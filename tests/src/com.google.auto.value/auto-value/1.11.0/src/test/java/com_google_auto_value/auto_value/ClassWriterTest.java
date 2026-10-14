/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.org.objectweb.asm.ClassWriter;
import org.junit.jupiter.api.Test;

public class ClassWriterTest {
    @Test
    void resolvesACommonSuperclassUsingTheConfiguredClassLoader() {
        CommonSuperclassWriter writer = new CommonSuperclassWriter();

        String commonType = writer.commonSuperclass("java/util/ArrayList", "java/util/LinkedList");

        assertThat(commonType).isEqualTo("java/util/AbstractList");
    }

    private static final class CommonSuperclassWriter extends ClassWriter {
        private CommonSuperclassWriter() {
            super(0);
        }

        private String commonSuperclass(String left, String right) {
            return getCommonSuperClass(left, right);
        }
    }
}
