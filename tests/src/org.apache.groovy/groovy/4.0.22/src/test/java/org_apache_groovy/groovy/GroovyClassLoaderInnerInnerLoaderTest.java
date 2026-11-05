/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class GroovyClassLoaderInnerInnerLoaderTest {
    @Test
    void delegatesExistingClassesAndResourcesToItsParent() throws Exception {
        try (GroovyClassLoader parent = new GroovyClassLoader(getClass().getClassLoader());
                GroovyClassLoader.InnerLoader loader = new GroovyClassLoader.InnerLoader(parent)) {
            assertSame(Entry.class, loader.loadClass(Entry.class.getName()));
            assertSame(Entry.class, loader.loadClass(Entry.class.getName(), false, true, false));
            String name = "org_apache_groovy/groovy/messages.properties";
            URL resource = loader.getResource(name);
            Properties messages = new Properties();
            try (InputStream input = resource.openStream()) {
                messages.load(input);
            }
            assertEquals("Number of entries", messages.getProperty("count"));
            assertFalse(Collections.list(loader.getResources(name)).isEmpty());
        }
    }

    public static class Entry {
    }
}
