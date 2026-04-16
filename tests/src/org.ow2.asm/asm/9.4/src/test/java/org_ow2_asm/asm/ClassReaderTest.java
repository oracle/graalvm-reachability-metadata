/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ClassReaderTest {
    @Test
    void readsClassBytesFromTheSystemClassLoader() throws IOException {
        ClassReader classReader = new ClassReader(ClassReader.class.getName());

        assertThat(classReader.getClassName()).isEqualTo("org/objectweb/asm/ClassReader");
        assertThat(classReader.getSuperName()).isEqualTo("java/lang/Object");
    }
}
