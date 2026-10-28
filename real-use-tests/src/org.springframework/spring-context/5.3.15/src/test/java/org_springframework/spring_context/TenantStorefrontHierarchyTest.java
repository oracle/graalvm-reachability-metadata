/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.support.GenericApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Spring hierarchical application contexts to host isolated storefronts sharing a parent inventory catalog.
public class TenantStorefrontHierarchyTest {

    @Test
    @Timeout(60)
    public void overridesStorefrontBrandingWithoutReplacingTheSharedInventory() {
        try (GenericApplicationContext platform = new GenericApplicationContext()) {
            platform.registerBean("catalog", InventoryCatalog.class,
                    () -> new InventoryCatalog(Map.of("coffee", 10, "tea", 6)));
            platform.registerBean("branding", Branding.class, () -> new Branding("Platform Market"));
            platform.refresh();

            try (GenericApplicationContext north = storefront(platform, new Branding("North Market"));
                    GenericApplicationContext south = storefront(platform, null)) {
                CheckoutService northCheckout = north.getBean(CheckoutService.class);
                CheckoutService southCheckout = south.getBean(CheckoutService.class);

                assertEquals(new Receipt("North Market", "coffee", 2, 8), northCheckout.buy("coffee", 2));
                assertEquals(new Receipt("Platform Market", "coffee", 3, 5), southCheckout.buy("coffee", 3));
                assertEquals(new Receipt("North Market", "tea", 1, 5), northCheckout.buy("tea", 1));

                IllegalArgumentException shortage = assertThrows(IllegalArgumentException.class,
                        () -> northCheckout.buy("coffee", 6));
                assertEquals("Insufficient stock for coffee", shortage.getMessage());
                assertEquals(new Receipt("Platform Market", "coffee", 1, 4), southCheckout.buy("coffee", 1));
            }

            // Replacing tenant contexts must preserve parent inventory and discard tenant overrides.
            try (GenericApplicationContext replacement = storefront(platform, null)) {
                CheckoutService checkout = replacement.getBean(CheckoutService.class);
                assertEquals(new Receipt("Platform Market", "coffee", 1, 3), checkout.buy("coffee", 1));
                assertEquals(new Receipt("Platform Market", "tea", 2, 3), checkout.buy("tea", 2));
            }
        }
    }

    private static GenericApplicationContext storefront(GenericApplicationContext platform, Branding branding) {
        GenericApplicationContext context = new GenericApplicationContext(platform);
        try {
            if (branding != null) {
                context.registerBean("branding", Branding.class, () -> branding);
            }
            context.registerBeanDefinition("checkout", BeanDefinitionBuilder
                    .genericBeanDefinition(CheckoutService.class)
                    .addConstructorArgReference("catalog")
                    .addConstructorArgReference("branding")
                    .getBeanDefinition());
            context.refresh();
            return context;
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    public record Branding(String name) {
    }

    public record Receipt(String storefront, String product, int quantity, int remainingStock) {
    }

    public static final class InventoryCatalog {
        private final Map<String, Integer> stock;

        public InventoryCatalog(Map<String, Integer> initialStock) {
            stock = new HashMap<>(initialStock);
        }

        public int reserve(String product, int quantity) {
            if (quantity < 1) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            int available = stock.getOrDefault(product, 0);
            if (quantity > available) {
                throw new IllegalArgumentException("Insufficient stock for " + product);
            }
            int remaining = available - quantity;
            stock.put(product, remaining);
            return remaining;
        }
    }

    public static final class CheckoutService {
        private final InventoryCatalog catalog;
        private final Branding branding;

        public CheckoutService(InventoryCatalog catalog, Branding branding) {
            this.catalog = catalog;
            this.branding = branding;
        }

        public Receipt buy(String product, int quantity) {
            return new Receipt(branding.name(), product, quantity, catalog.reserve(product, quantity));
        }
    }
}
