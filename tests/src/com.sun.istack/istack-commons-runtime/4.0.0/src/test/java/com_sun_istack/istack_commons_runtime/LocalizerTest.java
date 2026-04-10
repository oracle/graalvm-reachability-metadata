/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_istack.istack_commons_runtime;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.sun.istack.localization.LocalizableMessageFactory;
import com.sun.istack.localization.Localizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizerTest {

    private static final String TOP_LEVEL_BUNDLE_NAME =
        "com_sun_istack.istack_commons_runtime.localization.IstackLocalizerTopLevelMessages";
    private static final String CONTEXT_LOADER_BUNDLE_NAME =
        "com_sun_istack.istack_commons_runtime.localization.IstackLocalizerContextMessages";

    @Test
    void localizeFallsBackToTopLevelBundleName() {
        Localizer localizer = new Localizer(Locale.ENGLISH);
        LocalizableMessageFactory messageFactory = new LocalizableMessageFactory(TOP_LEVEL_BUNDLE_NAME);

        String localized = localizer.localize(messageFactory.getMessage("greeting", "world"));

        assertThat(localized).isEqualTo("Hello world");
    }

    @Test
    void localizeFallsBackToContextClassLoaderBundleLookup(@TempDir Path tempDir) throws IOException {
        Path bundleDirectory = tempDir.resolve("com_sun_istack/istack_commons_runtime/localization");
        Files.createDirectories(bundleDirectory);
        Files.writeString(
            bundleDirectory.resolve("IstackLocalizerContextMessages.properties"),
            "greeting=Hello from context loader {0}\n"
        );

        Localizer localizer = new Localizer(Locale.ENGLISH);
        LocalizableMessageFactory messageFactory = new LocalizableMessageFactory(CONTEXT_LOADER_BUNDLE_NAME);

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader contextClassLoader = new URLClassLoader(new URL[] { tempDir.toUri().toURL() }, null)) {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            String localized = localizer.localize(messageFactory.getMessage("greeting", "world"));

            assertThat(localized).isEqualTo("Hello from context loader world");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
