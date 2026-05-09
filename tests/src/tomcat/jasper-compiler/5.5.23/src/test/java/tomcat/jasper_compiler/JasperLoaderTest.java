/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;

import org.apache.jasper.servlet.JasperLoader;
import org.junit.jupiter.api.Test;

public class JasperLoaderTest {
    @Test
    void loadClassDelegatesNonJspClassesToParentLoader() throws Exception {
        final ClassLoader parent = JasperLoaderTest.class.getClassLoader();
        try (JasperLoader loader = new JasperLoader(new URL[0], parent, null, null)) {
            final Class<?> loadedClass = loader.loadClass(String.class.getName());

            assertThat(loadedClass).isSameAs(String.class);
        }
    }

    @Test
    void getResourceAsStreamAsksParentLoaderFirst() throws Exception {
        final ClassLoader parent = JasperLoaderTest.class.getClassLoader();
        try (JasperLoader loader = new JasperLoader(new URL[0], parent, null, null);
                InputStream stream = loader.getResourceAsStream("missing-jasper-loader-test-resource.txt")) {
            assertThat(stream).isNull();
        }
    }
}
