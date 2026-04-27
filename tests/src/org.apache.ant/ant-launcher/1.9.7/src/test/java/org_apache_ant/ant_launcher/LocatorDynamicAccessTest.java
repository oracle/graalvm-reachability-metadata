/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_launcher;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.launch.Locator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class LocatorDynamicAccessTest {

    @Test
    void getResourceSourceUsesProvidedClassLoader(@TempDir final Path tempDir) throws Exception {
        Path resourcePath = Files.writeString(tempDir.resolve("ant-launcher-resource.txt"), "marker");
        ClassLoader resourceLoader = new ClassLoader(null) {
            @Override
            public URL getResource(final String name) {
                if (!resourcePath.getFileName().toString().equals(name)) {
                    return null;
                }
                try {
                    return resourcePath.toUri().toURL();
                } catch (final MalformedURLException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        };

        File resourceSource = Locator.getResourceSource(resourceLoader, resourcePath.getFileName().toString());

        assertThat(resourceSource).isEqualTo(tempDir.toFile());
    }
}
