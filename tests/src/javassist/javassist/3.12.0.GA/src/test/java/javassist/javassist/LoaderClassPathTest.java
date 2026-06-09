/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;

import javassist.LoaderClassPath;

import org.junit.jupiter.api.Test;

public class LoaderClassPathTest {
    @Test
    void opensClassfileFromConfiguredLoader() throws Exception {
        LoaderClassPath classPath = new LoaderClassPath(LoaderClassPathTest.class.getClassLoader());

        try (InputStream classfile = classPath.openClassfile(LoaderClassPathTest.class.getName())) {
            assertThat(classfile).isNotNull();
            assertThat(classfile.readNBytes(4)).containsExactly(
                    (byte) 0xCA,
                    (byte) 0xFE,
                    (byte) 0xBA,
                    (byte) 0xBE);
        } finally {
            classPath.close();
        }
    }

    @Test
    void findsClassfileUrlFromConfiguredLoader() {
        LoaderClassPath classPath = new LoaderClassPath(LoaderClassPathTest.class.getClassLoader());

        try {
            URL classfile = classPath.find(LoaderClassPathTest.class.getName());

            assertThat(classfile).isNotNull();
            assertThat(classfile.toExternalForm()).contains("LoaderClassPathTest.class");
        } finally {
            classPath.close();
        }
    }
}
