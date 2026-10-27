/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

import org.eclipse.jetty.util.Loader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoaderTest {
    private static final String BUNDLE_NAME = "org_eclipse_jetty.jetty_util.loader";
    private static final String LOADER_RESOURCE = "org/eclipse/jetty/util/Loader.class";

    @Test
    void loadsClassesResourcesAndBundlesWithAvailableLoaders() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(LoaderTest.class.getClassLoader());

            URL contextResource = Loader.getResource(LOADER_RESOURCE);
            assertThat(contextResource).isNotNull();
            assertThat(Loader.loadClass(String.class.getName())).isSameAs(String.class);
            assertThat(Loader.loadClass(Loader.class, String.class.getName())).isSameAs(String.class);
            ResourceBundle contextBundle = Loader.getResourceBundle(BUNDLE_NAME, true, Locale.ROOT);
            assertThat(contextBundle.getString("greeting")).isEqualTo("hello from Jetty");

            thread.setContextClassLoader(null);

            URL systemResource = Loader.getResource(LOADER_RESOURCE);
            assertThat(systemResource).isNotNull();
            assertThat(Loader.loadClass(String.class.getName())).isSameAs(String.class);
            ResourceBundle systemBundle = Loader.getResourceBundle(BUNDLE_NAME, true, Locale.ROOT);
            assertThat(systemBundle.getString("greeting")).isEqualTo("hello from Jetty");
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }
}
