/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.context.support.ResourceBundleMessageSource;

public class ResourceBundleMessageSourceInnerMessageSourceControlTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reloadsPropertiesBundleThroughResourceUrl() throws IOException {
        String basename = "reloadable_messages";
        Path propertiesFile = this.temporaryDirectory.resolve(basename + ".properties");
        Files.writeString(propertiesFile, "greeting=initial message\n", StandardCharsets.ISO_8859_1);

        ClassLoader classLoader = new DirectoryResourceClassLoader(
                propertiesFile.getParent(), getClass().getClassLoader());
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBundleClassLoader(classLoader);
        messageSource.setCacheMillis(0);
        messageSource.setBasename(basename);

        assertEquals("initial message", messageSource.getMessage("greeting", null, Locale.ROOT));

        Files.writeString(propertiesFile, "greeting=reloaded message\n", StandardCharsets.ISO_8859_1);
        Files.setLastModifiedTime(propertiesFile, FileTime.from(Instant.now().plusSeconds(5)));

        try {
            assertEquals("reloaded message", messageSource.getMessage("greeting", null, Locale.ROOT));
        }
        finally {
            ResourceBundle.clearCache(classLoader);
        }
    }

    private static final class DirectoryResourceClassLoader extends ClassLoader {

        private final Path resourceDirectory;

        private DirectoryResourceClassLoader(Path resourceDirectory, ClassLoader parent) {
            super(parent);
            this.resourceDirectory = resourceDirectory;
        }

        @Override
        public URL getResource(String name) {
            URL resource = findDirectoryResource(name);
            return (resource != null ? resource : super.getResource(name));
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            Path resource = this.resourceDirectory.resolve(name);
            if (Files.isRegularFile(resource)) {
                try {
                    return Files.newInputStream(resource);
                }
                catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
            return super.getResourceAsStream(name);
        }

        private URL findDirectoryResource(String name) {
            Path resource = this.resourceDirectory.resolve(name);
            if (!Files.isRegularFile(resource)) {
                return null;
            }
            try {
                return resource.toUri().toURL();
            }
            catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }
}
