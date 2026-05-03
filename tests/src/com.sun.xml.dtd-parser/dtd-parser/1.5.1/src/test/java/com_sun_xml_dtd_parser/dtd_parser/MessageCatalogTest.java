/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_dtd_parser.dtd_parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.xml.dtdparser.MessageCatalog;

import com_sun_xml_dtd_parser.dtd_parser.bootstrap.BootstrapMessageCatalog;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;

public class MessageCatalogTest {
    private static final Locale UNSUPPORTED_LOCALE = Locale.forLanguageTag("zz-ZZ");

    private final TestCatalog catalog = new TestCatalog();

    @Test
    void readsMessagesFromLocalizedResourceBundle() {
        String message = catalog.getMessage(Locale.ENGLISH, "plain");
        String formatted = catalog.getMessage(Locale.ENGLISH, "formatted",
                new Object[] {"alpha", 3, new StringBuilder("builder"), null});

        assertThat(message).isEqualTo("plain message");
        assertThat(formatted).isEqualTo("formatted alpha 3 builder (null)");
    }

    @Test
    void fallsBackToEnglishResourceBundleForUnsupportedLocale() {
        Locale previousDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);
            clearBundleCache();

            String message = catalog.getMessage(UNSUPPORTED_LOCALE, "plain");
            String formatted = catalog.getMessage(UNSUPPORTED_LOCALE, "formatted", new Object[] {"fallback"});

            assertThat(message).isEqualTo("plain message");
            assertThat(formatted).isEqualTo("formatted fallback {1} {2} {3}");
        } finally {
            Locale.setDefault(previousDefault);
            clearBundleCache();
        }
    }

    @Test
    void detectsSupportedLocalesFromPropertyResource() {
        assertThat(catalog.isLocaleSupported("en")).isTrue();
        assertThat(catalog.isLocaleSupported("zz_ZZ")).isFalse();
        assertThat(catalog.chooseLocale(new String[] {"zh-cmn", "EN-us", "FR"})).isEqualTo(Locale.US);
    }

    @Test
    void checksLocaleSupportWithBootstrapLoadedCatalog() {
        if (Boolean.getBoolean("messageCatalog.bootstrapExpected")) {
            assertThat(BootstrapMessageCatalog.isLoadedByBootstrap()).isTrue();
        }

        assertThat(BootstrapMessageCatalog.INSTANCE.isLocaleSupported("zz")).isFalse();
    }

    private static void clearBundleCache() {
        ClassLoader classLoader = MessageCatalogTest.class.getClassLoader();
        if (classLoader == null) {
            ResourceBundle.clearCache();
        } else {
            ResourceBundle.clearCache(classLoader);
        }
    }

    private static final class TestCatalog extends MessageCatalog {
        private TestCatalog() {
            super(TestCatalog.class);
        }
    }
}
