/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http;

import org.glassfish.grizzly.http.util.StringManager;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class StringManagerTest {
    private static final String BUNDLE_PACKAGE = "org_glassfish_grizzly.grizzly_http.stringmanager";

    @Test
    void loadsBundleForRequestedLocale() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        StringManager manager = StringManager.getManager(BUNDLE_PACKAGE, Locale.US, classLoader);

        assertThat(manager.getString("plain.message")).isEqualTo("Message loaded from the US bundle");
        assertThat(manager.getString("formatted.message", "Grizzly")).isEqualTo("Hello, Grizzly");
    }

    @Test
    void fallsBackToUsBundleWhenRequestedLocaleIsMissing() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Locale originalDefault = Locale.getDefault();
        Locale requestedLocale = Locale.GERMANY;
        try {
            Locale.setDefault(requestedLocale);

            StringManager manager = StringManager.getManager(BUNDLE_PACKAGE, requestedLocale, classLoader);

            assertThat(manager.getString("plain.message")).isEqualTo("Message loaded from the US bundle");
        } finally {
            Locale.setDefault(originalDefault);
        }
    }
}
