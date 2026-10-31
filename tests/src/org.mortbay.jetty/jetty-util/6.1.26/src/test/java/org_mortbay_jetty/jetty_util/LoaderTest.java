/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;
import org.mortbay.util.Loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LoaderTest {
    private static final String RESOURCE = "org_mortbay_jetty/jetty_util/loader-message.txt";
    private static final String BUNDLE = "org_mortbay_jetty.jetty_util.LoaderMessages";

    @Test
    public void resolvesClassesResourcesAndBundlesWithContextLoader() throws Exception {
        assertEquals(String.class, Loader.loadClass(LoaderTest.class, "java.lang.String", true));
        assertResource(Loader.getResource(LoaderTest.class, RESOURCE, true));
        ResourceBundle bundle = Loader.getResourceBundle(LoaderTest.class, BUNDLE, true, Locale.ROOT);
        assertEquals("Welcome to Jetty", bundle.getString("welcome"));
    }

    @Test
    public void resolvesFromAnchorAndSystemWithoutContextLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            assertEquals(String.class, Loader.loadClass(LoaderTest.class, "java.lang.String"));
            assertResource(Loader.getResource(LoaderTest.class, RESOURCE, false));
            assertEquals("Welcome to Jetty",
                    Loader.getResourceBundle(LoaderTest.class, BUNDLE, false, Locale.ROOT).getString("welcome"));
            assertEquals(String.class, Loader.loadClass(null, "java.lang.String"));
            assertResource(Loader.getResource(null, RESOURCE, false));
            assertEquals("Welcome to Jetty",
                    Loader.getResourceBundle(null, BUNDLE, false, Locale.ROOT).getString("welcome"));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static void assertResource(URL resource) throws Exception {
        assertNotNull(resource);
        try (InputStream input = resource.openStream()) {
            assertEquals("Jetty resource payload", new String(input.readAllBytes(), StandardCharsets.UTF_8).trim());
        }
    }
}
