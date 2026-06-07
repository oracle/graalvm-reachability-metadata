/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.io.FileHandler;

public class FileHandlerTest {
    private static final String RESOURCE_NAME = "file-handler-resource.txt";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void copiesResourceLocatedByTheProvidedClassLoader() throws IOException {
        FileHandler.copyResource(temporaryDirectory.toFile(), FileHandlerTest.class, RESOURCE_NAME);

        Path copiedResource = temporaryDirectory.resolve(RESOURCE_NAME);
        assertThat(copiedResource).isRegularFile();
        assertThat(Files.readString(copiedResource, StandardCharsets.UTF_8))
                .isEqualTo("resource copied by Selenium FileHandler\n");
    }

    @Test
    void failsWhenResourceCannotBeLocatedByEitherClassLoader() {
        String missingResourceName = "missing-file-handler-resource.txt";

        assertThatIOException()
                .isThrownBy(
                        () ->
                                FileHandler.copyResource(
                                        temporaryDirectory.toFile(), FileHandlerTest.class, missingResourceName))
                .withMessageContaining("Unable to locate: " + missingResourceName);
    }
}
