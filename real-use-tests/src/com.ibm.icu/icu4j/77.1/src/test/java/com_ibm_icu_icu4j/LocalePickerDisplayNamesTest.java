/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's locale-display-name subsystem to label locales in a language picker.
public class LocalePickerDisplayNamesTest {
    @Test
    public void labelsLocaleChoicesInThePickerLanguage() {
        LocalePicker picker = new LocalePicker(ULocale.ENGLISH);

        assertEquals("Canadian French", picker.label("fr-CA"));
        assertEquals("Traditional Chinese (Taiwan)", picker.label("zh-Hant-TW"));
        assertEquals("United States", picker.regionLabel("US"));
    }

    private static final class LocalePicker {
        private final LocaleDisplayNames displayNames;

        private LocalePicker(ULocale pickerLocale) {
            displayNames = LocaleDisplayNames.getInstance(
                    pickerLocale, LocaleDisplayNames.DialectHandling.DIALECT_NAMES);
        }

        private String label(String languageTag) {
            return displayNames.localeDisplayName(ULocale.forLanguageTag(languageTag));
        }

        private String regionLabel(String regionCode) {
            return displayNames.regionDisplayName(regionCode);
        }
    }
}
