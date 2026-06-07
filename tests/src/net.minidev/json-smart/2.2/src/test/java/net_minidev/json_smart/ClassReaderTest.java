/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

public class ClassReaderTest {
    @Test
    void readsClassFromSystemResourceByBinaryName() throws IOException {
        ClassReader classReader = new ClassReader(ClassReader.class.getName());

        assertThat(classReader.getClassName()).isEqualTo("org/objectweb/asm/ClassReader");
        assertThat(classReader.getSuperName()).isEqualTo("java/lang/Object");
    }
}
