/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import net.sf.ehcache.util.ProductInfo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProductInfoTest {
    private static final String UNKNOWN = "UNKNOWN";

    @Test
    void loadsBundledProductInformationResource() {
        ProductInfo productInfo = new ProductInfo();

        assertKnownValue(productInfo.getName());
        assertKnownValue(productInfo.getVersion());
        assertKnownValue(productInfo.getBuiltBy());
        assertKnownValue(productInfo.getBuildJdk());
        assertKnownValue(productInfo.getBuildTime());
        assertKnownValue(productInfo.getBuildRevision());
        assertFalse(productInfo.isEnterprise());
        assertDoesNotThrow(productInfo::assertRequiredCoreVersionPresent);

        String description = productInfo.toString();
        assertTrue(description.contains(productInfo.getName()));
        assertTrue(description.contains(productInfo.getVersion()));
    }

    @Test
    void reportsMissingProductInformationResource() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> new ProductInfo("/missing-ehcache-product-info.properties"));

        assertEquals("Can't find resource: /missing-ehcache-product-info.properties", exception.getMessage());
    }

    private static void assertKnownValue(String value) {
        assertNotNull(value);
        assertFalse(value.trim().isEmpty());
        assertNotEquals(UNKNOWN, value);
    }
}
