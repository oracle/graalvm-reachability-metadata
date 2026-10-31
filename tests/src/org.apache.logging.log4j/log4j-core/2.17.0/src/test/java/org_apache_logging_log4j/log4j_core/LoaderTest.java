/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.core.util.Loader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LoaderTest {
    private static final String RESOURCE = "org_apache_logging_log4j/log4j_core/loader.txt";

    @Test
    void readsApplicationResourcesAsUrlsAndStreams() throws Exception {
        assertNotNull(Loader.getResource(RESOURCE, LoaderTest.class.getClassLoader()));
        try (InputStream input = Loader.getResourceAsStream(RESOURCE, LoaderTest.class.getClassLoader())) {
            assertNotNull(input);
            assertEquals("configured resource\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void loadsApplicationAndSystemTypesThroughTheDocumentedLoaders() throws Exception {
        ClassLoader loader = LoaderTest.class.getClassLoader();
        assertEquals(Settings.class, Loader.initializeClass(Settings.class.getName(), loader));
        assertEquals(Settings.class, Loader.loadClass(Settings.class.getName(), loader));
        assertEquals(String.class, Loader.loadSystemClass("java.lang.String"));
        Settings settings = Loader.newCheckedInstanceOf(Settings.class.getName(), Settings.class);
        assertEquals("ready", settings.status);
    }

    public static class Settings {
        public final String status = "ready";
    }
}
