/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.Transliterator;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's transliteration subsystem to build accent-insensitive customer search keys.
public class CustomerSearchTransliterationTest {
    @Test
    public void createsSearchKeysForNamesInDifferentScripts() {
        CustomerSearchIndex searchIndex = new CustomerSearchIndex();

        assertEquals("creme brulee", searchIndex.keyFor("Cr\u00e8me br\u00fbl\u00e9e"));
        assertEquals("maria sarapova", searchIndex.keyFor("\u041c\u0430\u0440\u0438\u044f \u0428\u0430\u0440\u0430\u043f\u043e\u0432\u0430"));
        assertEquals("dong jing", searchIndex.keyFor("\u6771\u4eac"));
    }

    private static final class CustomerSearchIndex {
        private final Transliterator transliterator =
                Transliterator.getInstance("Any-Latin; Latin-ASCII");

        private String keyFor(String customerName) {
            String latinName = transliterator.transliterate(customerName);
            return latinName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        }
    }
}
