/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;

import org.hsqldb.lib.RefCapablePropertyResourceBundle;
import org.junit.jupiter.api.Test;

public class RefCapablePropertyResourceBundleTest {
    private static final String BUNDLE_NAME =
            "org_hsqldb.hsqldb.RefCapablePropertyResourceBundleTestMessages";

    @Test
    public void loadsPropertyResourceBundleWithExplicitLocaleAndClassLoader() {
        ClassLoader classLoader = RefCapablePropertyResourceBundleTest.class.getClassLoader();

        RefCapablePropertyResourceBundle bundle = RefCapablePropertyResourceBundle.getBundle(
                BUNDLE_NAME,
                Locale.US,
                classLoader);
        RefCapablePropertyResourceBundle cachedBundle = RefCapablePropertyResourceBundle.getBundle(
                BUNDLE_NAME,
                Locale.US,
                classLoader);

        assertSame(bundle, cachedBundle);
        assertEquals("inline value", bundle.getString("inline"));
        assertEquals("Referenced line one" + RefCapablePropertyResourceBundle.LS + "Referenced line two",
                bundle.getString("referenced"));
    }

    @Test
    public void expandsSystemAndPositionalPlaceholdersFromLoadedBundle() {
        ClassLoader classLoader = RefCapablePropertyResourceBundleTest.class.getClassLoader();
        RefCapablePropertyResourceBundle bundle = RefCapablePropertyResourceBundle.getBundle(
                BUNDLE_NAME,
                Locale.US,
                classLoader);
        String propertyName = "hsqldb.refbundle.coverage.name";
        String previousValue = System.getProperty(propertyName);

        try {
            System.setProperty(propertyName, "coverage");

            assertEquals("Hello coverage",
                    bundle.getExpandedString("system", RefCapablePropertyResourceBundle.THROW_BEHAVIOR));
            assertEquals("Coordinate org.hsqldb:hsqldb uses metadata",
                    bundle.getString("positional", new String[] {"org.hsqldb:hsqldb", "metadata"},
                            RefCapablePropertyResourceBundle.THROW_BEHAVIOR));
            assertEquals("Feature bundles enabled",
                    bundle.getString("conditional", new String[] {"bundles"},
                            RefCapablePropertyResourceBundle.THROW_BEHAVIOR));
        } finally {
            if (previousValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousValue);
            }
        }
    }
}
