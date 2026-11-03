/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.FunctionUtils;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Composes and recovers application functions with Resilience4j's function utility subsystem.
 *
 * A catalog client converts a remote inventory response into the checkout view and supplies a
 * stable unavailable view when the catalog endpoint fails, so callers do not handle that failure.
 */
class FunctionCompositionTest {

    @Test
    void buildsCheckoutViewFromCatalogAndUsesUnavailableFallback() {
        Function<String, CatalogItem> catalogLookup = sku -> {
            if (sku.equals("SKU-42")) {
                return new CatalogItem(sku, 7);
            }
            throw new CatalogUnavailableException();
        };
        Function<String, CheckoutInventory> checkoutInventory = FunctionUtils.andThen(
                catalogLookup,
                CatalogItem::toCheckoutInventory,
                failure -> CheckoutInventory.unavailable());

        assertThat(checkoutInventory.apply("SKU-42"))
                .isEqualTo(new CheckoutInventory("SKU-42", 7, true));
        assertThat(checkoutInventory.apply("SKU-missing"))
                .isEqualTo(new CheckoutInventory("unavailable", 0, false));
    }

    private record CatalogItem(String sku, int availableUnits) {

        private CheckoutInventory toCheckoutInventory() {
            return new CheckoutInventory(sku, availableUnits, true);
        }
    }

    private record CheckoutInventory(String sku, int availableUnits, boolean catalogAvailable) {

        private static CheckoutInventory unavailable() {
            return new CheckoutInventory("unavailable", 0, false);
        }
    }

    private static final class CatalogUnavailableException extends RuntimeException {
    }
}
