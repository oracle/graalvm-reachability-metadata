/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javassist.ClassClassPath;
import javassist.ClassPath;
import javassist.ClassPool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassClassPathTest {
    @Test
    void openClassfileReadsClassResourceThroughAnchorClass() throws IOException {
        ClassClassPath classPath = new ClassClassPath(ClassClassPathTest.class);
        InputStream stream = classPath.openClassfile(ClassClassPath.class.getName());

        assertThat(stream).isNotNull();
        try (InputStream classfile = stream) {
            assertThat(classfile.read()).isEqualTo(0xCA);
            assertThat(classfile.read()).isEqualTo(0xFE);
            assertThat(classfile.read()).isEqualTo(0xBA);
            assertThat(classfile.read()).isEqualTo(0xBE);
        }
    }

    @Test
    void findReturnsClassResourceUrlThroughAnchorClass() {
        ClassClassPath classPath = new ClassClassPath(ClassClassPathTest.class);

        URL resource = classPath.find(ClassClassPath.class.getName());

        assertThat(resource).isNotNull();
        assertThat(resource.toExternalForm()).contains("javassist/ClassClassPath.class");
    }

    @Test
    void appendSystemPathCreatesDefaultClassClassPath() {
        ClassPool classPool = new ClassPool();

        ClassPath classPath = classPool.appendSystemPath();

        assertThat(classPath).isInstanceOf(ClassClassPath.class);
        assertThat(classPath.toString()).isEqualTo("java.lang.Object.class");
    }
}
