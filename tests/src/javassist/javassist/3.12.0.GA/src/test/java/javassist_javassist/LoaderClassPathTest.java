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

import javassist.LoaderClassPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoaderClassPathTest {
    @Test
    void openClassfileReadsClassResourceThroughSuppliedClassLoader() throws IOException {
        LoaderClassPath classPath = new LoaderClassPath(LoaderClassPathTest.class.getClassLoader());
        InputStream stream = classPath.openClassfile(LoaderClassPath.class.getName());

        assertThat(stream).isNotNull();
        try (InputStream classfile = stream) {
            assertThat(classfile.read()).isEqualTo(0xCA);
            assertThat(classfile.read()).isEqualTo(0xFE);
            assertThat(classfile.read()).isEqualTo(0xBA);
            assertThat(classfile.read()).isEqualTo(0xBE);
        }
    }

    @Test
    void findReturnsClassResourceUrlThroughSuppliedClassLoader() {
        LoaderClassPath classPath = new LoaderClassPath(LoaderClassPathTest.class.getClassLoader());

        URL resource = classPath.find(LoaderClassPath.class.getName());

        assertThat(resource).isNotNull();
        assertThat(resource.toExternalForm()).contains("javassist/LoaderClassPath.class");
    }
}
