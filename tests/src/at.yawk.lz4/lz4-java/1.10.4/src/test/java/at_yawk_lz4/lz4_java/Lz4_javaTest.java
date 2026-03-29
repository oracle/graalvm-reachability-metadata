/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Lz4_javaTest {
    @Test
    void shouldHaveLibraryJarOnTestClasspath() throws IOException {
        Enumeration<URL> manifests = Thread.currentThread()
                .getContextClassLoader()
                .getResources("META-INF/MANIFEST.MF");

        List<URL> manifestUrls = Collections.list(manifests);

        assertThat(manifestUrls)
                .isNotEmpty()
                .anySatisfy(url -> assertThat(url.toString()).contains("lz4-java-1.10.4.jar"));
    }
}
