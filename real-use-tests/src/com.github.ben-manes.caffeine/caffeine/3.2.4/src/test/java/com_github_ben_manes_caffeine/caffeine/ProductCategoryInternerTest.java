/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Interner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Exercises Caffeine's strong interning subsystem for a product-catalog service.
 * Equivalent category values share one canonical instance before being attached to products.
 */
public class ProductCategoryInternerTest {
    @Test
    @Timeout(60)
    public void reusesOneCanonicalCategoryForEquivalentCatalogEntries() {
        Interner<ProductCategory> categories = Interner.newStrongInterner();
        ProductCategory shoesCategory = new ProductCategory("footwear", "Shoes");
        ProductCategory duplicateCategory = new ProductCategory("footwear", "Shoes");
        ProductCategory apparelCategory = new ProductCategory("apparel", "Apparel");

        ProductCategory canonicalShoes = categories.intern(shoesCategory);
        ProductCategory duplicateCanonicalShoes = categories.intern(duplicateCategory);
        ProductCategory canonicalApparel = categories.intern(apparelCategory);

        assertSame(shoesCategory, canonicalShoes);
        assertSame(canonicalShoes, duplicateCanonicalShoes);
        assertSame(apparelCategory, canonicalApparel);
        assertNotSame(canonicalShoes, canonicalApparel);
    }

    private record ProductCategory(String code, String displayName) {
    }
}
