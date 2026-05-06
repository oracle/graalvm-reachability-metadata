/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClassReaderTest {
    @Test
    void readsClassBytesFromTheSystemClassLoader() throws IOException {
        ClassReader classReader = new ClassReader(ClassReader.class.getName());

        assertThat(classReader.b).isNotEmpty();
        assertThat(classReader.getClassName()).isEqualTo("org/objectweb/asm/ClassReader");
        assertThat(classReader.getSuperName()).isEqualTo("java/lang/Object");
        assertThat(classReader.getInterfaces()).isEmpty();
    }

    @Test
    void reportsMissingClassFromTheSystemClassLoader() {
        assertThatThrownBy(() -> new ClassReader("asm.asm.DoesNotExist"))
                .isInstanceOf(IOException.class)
                .hasMessage("Class not found");
    }
}
