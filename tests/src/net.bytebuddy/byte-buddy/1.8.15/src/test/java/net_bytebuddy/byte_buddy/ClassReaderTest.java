/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.jar.asm.ClassReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassReaderTest {
    @Test
    void readsClassFileFromSystemResourceByClassName() throws IOException {
        ClassReader classReader = new ClassReader(ClassReader.class.getName());

        assertThat(classReader.getClassName()).isEqualTo("net/bytebuddy/jar/asm/ClassReader");
        assertThat(classReader.getSuperName()).isEqualTo("java/lang/Object");
    }
}
