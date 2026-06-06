/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.ExtendedProperties;
import io.sundr.deps.org.apache.velocity.runtime.log.Log;
import io.sundr.deps.org.apache.velocity.runtime.log.NullLogChute;
import io.sundr.deps.org.apache.velocity.runtime.resource.loader.URLResourceLoader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class URLResourceLoaderTest {
    @TempDir
    Path templateRoot;

    @Test
    void configuredUrlLoaderFindsTemplateWithTimeoutsEnabled() throws Exception {
        Path template = templateRoot.resolve("hello.vm");
        Files.writeString(template, "Hello from URL loader", StandardCharsets.UTF_8);

        URLResourceLoader loader = new InitializedURLResourceLoader();
        loader.init(urlLoaderProperties());

        try (InputStream stream = loader.getResourceStream("hello.vm")) {
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(content).isEqualTo("Hello from URL loader");
        }
        assertThat(loader.getTimeout()).isEqualTo(250);
    }

    private ExtendedProperties urlLoaderProperties() throws Exception {
        ExtendedProperties properties = new ExtendedProperties();
        properties.setProperty("root", templateRoot.toUri().toURL().toString());
        properties.setProperty("timeout", "250");
        return properties;
    }

    private static final class InitializedURLResourceLoader extends URLResourceLoader {
        private InitializedURLResourceLoader() {
            log = new Log(new NullLogChute());
        }
    }
}
