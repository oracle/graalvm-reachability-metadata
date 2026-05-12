/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.ResourceBundle;
import org.codehaus.plexus.i18n.DefaultI18N;
import org.junit.jupiter.api.Test;

public class DefaultI18NTest {
    private static final String BUNDLE_NAME = "org_codehaus_plexus.plexus_i18n.PlexusMessages";

    @Test
    void loadsBundleForExplicitLocaleAndFormatsMessage() throws Exception {
        DefaultI18N i18n = newI18NWithDefaultLocale(Locale.US);

        ResourceBundle bundle = i18n.getBundle(BUNDLE_NAME, Locale.US);
        String formatted = i18n.format(BUNDLE_NAME, Locale.US, "greeting", "Plexus");

        assertThat(bundle.getString("greeting")).isEqualTo("Hello {0}");
        assertThat(formatted).isEqualTo("Hello Plexus");
    }

    @Test
    void developmentModeClearsResourceBundleCacheBeforeLookup() throws Exception {
        Locale originalLocale = Locale.getDefault();
        String originalDevMode = System.getProperty("PLEXUS_DEV_MODE");
        try {
            Locale.setDefault(Locale.US);
            System.setProperty("PLEXUS_DEV_MODE", "true");

            DefaultI18N i18n = new DefaultI18N();
            i18n.initialize();

            ResourceBundle bundle = i18n.getBundle(BUNDLE_NAME, Locale.US);

            assertThat(bundle.getString("plain")).isEqualTo("Hello");
        } finally {
            restoreProperty("PLEXUS_DEV_MODE", originalDevMode);
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void fallsBackToDefaultCountryWhenRequestedLocaleOnlyHasDefaultLanguage() throws Exception {
        Locale originalLocale = Locale.getDefault();
        try {
            DefaultI18N i18n = newI18NWithDefaultLocale(Locale.US);
            Locale.setDefault(Locale.FRANCE);

            ResourceBundle bundle = i18n.getBundle(BUNDLE_NAME, Locale.ENGLISH);

            assertThat(bundle.getLocale()).isEqualTo(Locale.US);
            assertThat(bundle.getString("plain")).isEqualTo("Hello");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    private static DefaultI18N newI18NWithDefaultLocale(Locale locale) throws Exception {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            DefaultI18N i18n = new DefaultI18N();
            i18n.initialize();
            return i18n;
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
