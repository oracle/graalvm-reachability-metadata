/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_dtd_parser.dtd_parser;

import java.util.Locale;

import com.sun.xml.dtdparser.MessageCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageCatalogTest {
    private final Catalog catalog = new Catalog();

    @Test
    void getMessageLoadsRequestedResourceBundle() {
        assertThat(catalog.getMessage(Locale.ENGLISH, "plain"))
                .isEqualTo("English plain message");
    }

    @Test
    void getMessageFallsBackToEnglishResourceBundle() {
        Locale previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.ROOT);
        try {
            assertThat(catalog.getMessage(Locale.GERMAN, "plain"))
                    .isEqualTo("English plain message");
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    void getMessageFormatsRequestedResourceBundle() {
        Object[] parameters = {"client", 3};

        assertThat(catalog.getMessage(Locale.ENGLISH, "formatted", parameters))
                .isEqualTo("Hello client, count 3");
    }

    @Test
    void getMessageFormatsFallbackResourceBundleAndNormalizesParameters() {
        Locale previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.ROOT);
        try {
            Object[] parameters = {new StringBuilder("builder"), null};

            assertThat(catalog.getMessage(Locale.ITALIAN, "normalized", parameters))
                    .isEqualTo("Normalized builder and (null)");
            assertThat(parameters).containsExactly("builder", "(null)");
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    void chooseLocaleFindsSupportedPropertyResource() {
        assertThat(catalog.chooseLocale(new String[] {"fr-ca", "en"}))
                .isEqualTo(Locale.CANADA_FRENCH);
    }

    @Test
    void chooseLocaleFindsSupportedClassResourceBundle() {
        assertThat(catalog.chooseLocale(new String[] {"es", "en"}))
                .isEqualTo(Locale.forLanguageTag("es"));
    }

    @Test
    void chooseLocaleSkipsUnsupportedAndInvalidLanguageTags() {
        assertThat(catalog.chooseLocale(new String[] {"x-private", "zz", "EN"}))
                .isEqualTo(Locale.ENGLISH);
        assertThat(catalog.chooseLocale(new String[] {"x-private", "zz"}))
                .isNull();
    }

    private static final class Catalog extends MessageCatalog {
        private Catalog() {
            super(Catalog.class);
        }
    }
}
