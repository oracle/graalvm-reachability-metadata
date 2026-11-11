/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import aj.org.objectweb.asm.ClassReader;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClassReaderTest {
    @Test
    void readsClassFromSystemResource() throws IOException {
        ClassReader classReader = new ClassReader("aj.org.objectweb.asm.ClassReader");

        assertThat(classReader.getClassName()).isEqualTo("aj/org/objectweb/asm/ClassReader");
        assertThat(classReader.getSuperName()).isEqualTo("java/lang/Object");
    }

    @Test
    void reportsMissingSystemResource() {
        assertThatThrownBy(() -> new ClassReader("example.missing.AspectJWeaverClass"))
                .isInstanceOf(IOException.class)
                .hasMessage("Class not found");
    }
}
