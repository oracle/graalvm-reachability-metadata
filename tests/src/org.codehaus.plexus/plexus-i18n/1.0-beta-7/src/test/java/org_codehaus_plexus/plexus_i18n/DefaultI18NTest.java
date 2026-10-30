/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_i18n;

import org.codehaus.plexus.i18n.DefaultI18N;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock("default-locale")
public class DefaultI18NTest {
    private static final String MESSAGES = "org_codehaus_plexus.plexus_i18n.messages";
    private static final String REGIONAL_MESSAGES = "org_codehaus_plexus.plexus_i18n.regional";

    @Test
    void loadsLocalizedBundlesAndCachesThemByLocale() throws Exception {
        DefaultI18N i18n = new DefaultI18N();
        i18n.initialize();

        ResourceBundle english = i18n.getBundle(MESSAGES, Locale.ENGLISH);
        ResourceBundle french = i18n.getBundle(MESSAGES, Locale.FRENCH);

        assertThat(english.getString("greeting")).isEqualTo("Hello");
        assertThat(french.getString("greeting")).isEqualTo("Bonjour");
        assertThat(french.getLocale()).isEqualTo(Locale.FRENCH);
        assertThat(i18n.getBundle(MESSAGES, Locale.FRENCH)).isSameAs(french);
        assertThat(i18n.getBundle("  " + MESSAGES + "  ", "fr;q=1.0,en;q=0.5")).isSameAs(french);
        assertThat(i18n.getString(MESSAGES, Locale.FRENCH, "rootOnly")).isEqualTo("Shared text");
        assertThat(i18n.getString(MESSAGES, Locale.ENGLISH, "greeting")).isEqualTo("Hello");
    }

    @Test
    void formatsTranslatedMessagesWithArguments() throws Exception {
        DefaultI18N i18n = new DefaultI18N();
        i18n.initialize();

        assertThat(i18n.format(MESSAGES, Locale.ENGLISH, "welcome", "Alex")).isEqualTo("Welcome, Alex!");
        assertThat(i18n.format(MESSAGES, Locale.FRENCH, "welcome", "Alex")).isEqualTo("Bienvenue, Alex !");
        assertThat(i18n.format(MESSAGES, Locale.ENGLISH, "route", "Paris", "Lyon"))
                .isEqualTo("From Paris to Lyon");
        assertThat(i18n.format(MESSAGES, Locale.FRENCH, "route", new Object[]{"Paris", "Lyon"}))
                .isEqualTo("De Paris vers Lyon");
    }

    @Test
    void fallsBackToConfiguredDefaultCountryWhenOnlyRegionalBundleExists() throws Exception {
        Locale original = Locale.getDefault();
        Locale originalDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.US);
            DefaultI18N i18n = new DefaultI18N();
            i18n.initialize();
            Locale.setDefault(Locale.ROOT);

            ResourceBundle bundle = i18n.getBundle(REGIONAL_MESSAGES, Locale.ENGLISH);

            assertThat(bundle.getLocale()).isEqualTo(Locale.US);
            assertThat(bundle.getString("greeting")).isEqualTo("Howdy");
            assertThat(i18n.getBundle(REGIONAL_MESSAGES, Locale.US)).isSameAs(bundle);
            assertThat(i18n.getString(REGIONAL_MESSAGES, Locale.FRENCH, "greeting")).isEqualTo("Howdy");
            assertThat(i18n.format(REGIONAL_MESSAGES, null, "welcome", "Sam")).isEqualTo("Howdy, Sam!");
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.DISPLAY, originalDisplay);
            Locale.setDefault(Locale.Category.FORMAT, originalFormat);
        }
    }
}
