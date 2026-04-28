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
    void appendsSystemPathUsingObjectClassResourceAnchor() {
        ClassPool classPool = new ClassPool(false);

        ClassPath systemPath = classPool.appendSystemPath();

        assertThat(systemPath).isInstanceOf(ClassClassPath.class);
        assertThat(systemPath.toString()).isEqualTo(Object.class.getName() + ".class");
    }

    @Test
    void opensClassfileRelativeToSuppliedClass() throws IOException {
        ClassClassPath classPath = new ClassClassPath(ClassClassPath.class);

        try (InputStream stream = classPath.openClassfile(ClassClassPath.class.getName())) {
            assertThat(stream).isNotNull();
            assertThat(stream.read()).isEqualTo(0xCA);
            assertThat(stream.read()).isEqualTo(0xFE);
            assertThat(stream.read()).isEqualTo(0xBA);
            assertThat(stream.read()).isEqualTo(0xBE);
        }
    }

    @Test
    void findsClassfileUrlRelativeToSuppliedClass() {
        ClassClassPath classPath = new ClassClassPath(ClassClassPath.class);

        URL resource = classPath.find(ClassClassPath.class.getName());

        assertThat(resource).isNotNull();
        assertThat(resource.toExternalForm()).endsWith("/javassist/ClassClassPath.class");
    }
}
