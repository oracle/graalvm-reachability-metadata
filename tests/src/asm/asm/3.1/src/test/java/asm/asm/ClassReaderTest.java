/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ClassReaderTest {
    @Test
    public void readsClassResourceWithSystemClassLoader() throws IOException {
        String classReaderName = String.join(".", "org", "objectweb", "asm", "ClassReader");

        ClassReader classReader = new ClassReader(classReaderName);

        assertThat(classReader.getClassName()).isEqualTo("org/objectweb/asm/ClassReader");
        assertThat(classReader.getSuperName()).isEqualTo("java/lang/Object");
    }

    @Test
    public void reportsMissingClassResourceAfterSystemClassLoaderLookup() {
        String missingClassName = String.join(".", "asm", "asm", "NoSuchClassReaderFixture");

        try {
            new ClassReader(missingClassName);
            fail("Expected ClassReader to report a missing class resource");
        } catch (IOException exception) {
            assertThat(exception).hasMessage("Class not found");
        }
    }
}
