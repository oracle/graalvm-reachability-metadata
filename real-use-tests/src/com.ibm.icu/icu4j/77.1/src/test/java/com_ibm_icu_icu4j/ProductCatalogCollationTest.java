/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's collation subsystem to sort a product catalog and compare customer search terms.
public class ProductCatalogCollationTest {
    @Test
    public void sortsCatalogNamesAndMatchesAccentVariants() {
        ProductCatalog catalog = new ProductCatalog();

        assertEquals(List.of("\u00C5lesund", "Amsterdam", "Z\u00FCrich"), catalog.sortedNames());
        assertEquals(0, catalog.compareSearchTerms("resume", "r\u00E9sum\u00E9"));
    }

    private static final class ProductCatalog {
        private final Collator searchCollator = Collator.getInstance(ULocale.ENGLISH);

        private ProductCatalog() {
            searchCollator.setStrength(Collator.PRIMARY);
        }

        private List<String> sortedNames() {
            List<String> names = new ArrayList<>(List.of("Z\u00FCrich", "\u00C5lesund", "Amsterdam"));
            names.sort(searchCollator::compare);
            return names;
        }

        private int compareSearchTerms(String first, String second) {
            return searchCollator.compare(first, second);
        }
    }
}
