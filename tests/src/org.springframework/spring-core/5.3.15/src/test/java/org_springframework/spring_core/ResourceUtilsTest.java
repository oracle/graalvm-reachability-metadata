/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

/** Verifies Spring's public classpath location resolution. §FS-repository-functional-spec.5.2 */
public class ResourceUtilsTest {
    private static final String RESOURCE_PATH = "org_springframework/spring_core/class-path-resource.txt";
    private static final String RESOURCE_LOCATION = ResourceUtils.CLASSPATH_URL_PREFIX + RESOURCE_PATH;
    private static final String MISSING_RESOURCE_LOCATION =
            ResourceUtils.CLASSPATH_URL_PREFIX + "org_springframework/spring_core/missing-resource.txt";

    @Test
    void resolvesClasspathLocationAsUrl() throws IOException {
        URL url = ResourceUtils.getURL(RESOURCE_LOCATION);

        try (InputStream input = url.openStream()) {
            assertThat(new String(input.readAllBytes(), UTF_8)).isEqualTo("Spring classpath resource content\n");
        }
    }

    @Test
    void reportsMissingClasspathLocationWhenResolvingFile() {
        assertThatExceptionOfType(FileNotFoundException.class)
                .isThrownBy(() -> ResourceUtils.getFile(MISSING_RESOURCE_LOCATION))
                .withMessageContaining("class path resource")
                .withMessageContaining("cannot be resolved to absolute file path");
    }
}
