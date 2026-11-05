/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.Normalizer2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's Unicode normalization subsystem for stable product names in a catalog.
public class ProductNameNormalizationTest {
    @Test
    public void canonicalizesProductNamesBeforeCatalogLookup() {
        ProductCatalog catalog = new ProductCatalog();

        assertEquals("Caf\u00E9", catalog.canonicalName("Cafe\u0301"));
        assertEquals("\u00C5ngstr\u00F6m", catalog.canonicalName("A\u030Angstro\u0308m"));
        assertEquals("\u5546\u54C1", catalog.canonicalName("\u5546\u54C1"));
    }

    private static final class ProductCatalog {
        private final Normalizer2 normalizer = Normalizer2.getNFCInstance();

        private String canonicalName(String productName) {
            return normalizer.normalize(productName);
        }
    }
}
