/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.hsqldb.lib.RefCapablePropertyResourceBundle;
import org.junit.jupiter.api.Test;

public class RefCapablePropertyResourceBundleTest {
    private static final String BUNDLE_NAME = "org_hsqldb.hsqldb.refcapable";

    @Test
    void getBundleLoadsPropertyResourceBundleWithDefaultLocaleAndClassLoader() {
        ClassLoader classLoader = RefCapablePropertyResourceBundleTest.class.getClassLoader();

        RefCapablePropertyResourceBundle bundle =
                RefCapablePropertyResourceBundle.getBundle(BUNDLE_NAME, classLoader);

        assertThat(bundle.getString("message")).isEqualTo("loaded from properties");
    }

    @Test
    void getBundleLoadsPropertyResourceBundleWithExplicitLocaleAndClassLoader() {
        ClassLoader classLoader = RefCapablePropertyResourceBundleTest.class.getClassLoader();

        RefCapablePropertyResourceBundle bundle =
                RefCapablePropertyResourceBundle.getBundle(BUNDLE_NAME, Locale.ROOT, classLoader);

        assertThat(bundle.getString("message")).isEqualTo("loaded from properties");
        assertThat(bundle.getString("parameterized", new String[] {"HSQLDB"},
                RefCapablePropertyResourceBundle.THROW_BEHAVIOR)).isEqualTo("Hello HSQLDB");
    }
}
