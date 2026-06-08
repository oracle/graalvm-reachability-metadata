/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

public class ClassReaderTest {
    @Test
    void readsClassBytesFromSystemClassLoaderResource() throws IOException {
        ClassReader reader = new ClassReader(ClassReader.class.getName());

        assertThat(unsignedByte(reader.b[0])).isEqualTo(0xCA);
        assertThat(unsignedByte(reader.b[1])).isEqualTo(0xFE);
        assertThat(unsignedByte(reader.b[2])).isEqualTo(0xBA);
        assertThat(unsignedByte(reader.b[3])).isEqualTo(0xBE);
        assertThat(reader.getClassName()).isEqualTo("org/objectweb/asm/ClassReader");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
        assertThat(reader.getAccess() & Opcodes.ACC_PUBLIC).isEqualTo(Opcodes.ACC_PUBLIC);
    }

    private static int unsignedByte(byte value) {
        return value & 0xFF;
    }
}
