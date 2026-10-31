/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.tools.RootLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class RootLoaderTest {
    @Test
    void delegatesExistingClassesAndClasspathResourcesToItsParent() throws Exception {
        try (RootLoader loader = new RootLoader(getClass().getClassLoader())) {
            assertSame(Properties.class, loader.loadClass(Properties.class.getName()));
            assertSame(Properties.class, loader.loadClass(Properties.class.getName()));
            Properties messages = new Properties();
            try (InputStream input = loader.getResource("org_apache_groovy/groovy/messages.properties").openStream()) {
                messages.load(input);
            }
            assertEquals("Number of entries", messages.getProperty("count"));
        }
    }
}
