/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.io.InputStream;
import java.net.URL;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.StringUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class LoaderTest {
    @Test
    void loadsConfiguredClassesFromTheContextAndRelatedClassLoaders() throws Exception {
        assertSame(StringUtil.class, Loader.loadClass("org.eclipse.jetty.util.StringUtil"));
        assertSame(StringUtil.class, Loader.loadClass(Loader.class, "org.eclipse.jetty.util.StringUtil"));
    }

    @Test
    void loadsResourcesAndBundlesWithTheContextClassLoader() throws Exception {
        assertBuildProperties(Loader.getResource("org/eclipse/jetty/version/build.properties"));
        ResourceBundle bundle = Loader.getResourceBundle("org.eclipse.jetty.version.build", true, Locale.ROOT);
        assertFalse(bundle.getString("version").isBlank());
    }

    @Test
    void loadsClassesResourcesAndBundlesWithoutAContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            assertSame(StringUtil.class, Loader.loadClass("org.eclipse.jetty.util.StringUtil"));
            assertBuildProperties(Loader.getResource("org/eclipse/jetty/version/build.properties"));
            ResourceBundle bundle = Loader.getResourceBundle("org.eclipse.jetty.version.build", true, Locale.ROOT);
            assertFalse(bundle.getString("version").isBlank());
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static void assertBuildProperties(URL url) throws Exception {
        assertNotNull(url);
        Properties properties = new Properties();
        try (InputStream input = url.openStream()) {
            properties.load(input);
        }
        assertNotNull(properties.getProperty("version"));
        assertFalse(properties.getProperty("version").isBlank());
    }
}
