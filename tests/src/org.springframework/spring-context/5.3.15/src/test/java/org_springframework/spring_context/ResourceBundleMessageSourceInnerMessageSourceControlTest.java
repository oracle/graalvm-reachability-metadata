/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.context.support.ResourceBundleMessageSource;

public class ResourceBundleMessageSourceInnerMessageSourceControlTest {

    private static final String BASENAME = "dynamic.reload.messages";

    private static final String RESOURCE_NAME = "dynamic/reload/messages.properties";

    @Test
    void reloadsPropertiesBundleThroughClassLoaderResourceUrl(@TempDir Path temporaryDirectory) throws IOException {
        Path bundleFile = temporaryDirectory.resolve(RESOURCE_NAME);
        writeBundle(bundleFile, "initial");

        BundleResourceClassLoader classLoader = new BundleResourceClassLoader(RESOURCE_NAME, bundleFile);
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBundleClassLoader(classLoader);
        messageSource.setBasename(BASENAME);
        messageSource.setCacheMillis(0);

        String firstMessage = messageSource.getMessage("greeting", null, Locale.ROOT);
        writeBundle(bundleFile, "updated");
        Files.setLastModifiedTime(bundleFile, FileTime.from(Instant.now().plusSeconds(10)));

        String reloadedMessage = messageSource.getMessage("greeting", null, Locale.ROOT);

        assertEquals("initial", firstMessage);
        assertEquals("updated", reloadedMessage);
        assertTrue(classLoader.getResourceUrlLookups() > 0, "Reloading must ask the ClassLoader for the bundle URL");
    }

    private static void writeBundle(Path bundleFile, String message) throws IOException {
        Files.createDirectories(bundleFile.getParent());
        Files.writeString(bundleFile, "greeting=" + message + System.lineSeparator(), StandardCharsets.ISO_8859_1);
    }

    private static final class BundleResourceClassLoader extends ClassLoader {

        private final String resourceName;

        private final Path bundleFile;

        private int resourceUrlLookups;

        private BundleResourceClassLoader(String resourceName, Path bundleFile) {
            super(ResourceBundleMessageSourceInnerMessageSourceControlTest.class.getClassLoader());
            this.resourceName = resourceName;
            this.bundleFile = bundleFile;
        }

        @Override
        public URL getResource(String name) {
            if (this.resourceName.equals(name)) {
                this.resourceUrlLookups++;
                try {
                    return this.bundleFile.toUri().toURL();
                }
                catch (MalformedURLException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (this.resourceName.equals(name)) {
                try {
                    return Files.newInputStream(this.bundleFile);
                }
                catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
            return super.getResourceAsStream(name);
        }

        private int getResourceUrlLookups() {
            return this.resourceUrlLookups;
        }
    }
}
