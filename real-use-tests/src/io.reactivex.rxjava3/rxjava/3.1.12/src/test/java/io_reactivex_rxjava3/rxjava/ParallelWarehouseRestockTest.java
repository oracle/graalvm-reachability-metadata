/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ParallelFlowable processes a warehouse restock batch before the application publishes alerts. */
public class ParallelWarehouseRestockTest {
    @Test
    @Timeout(60)
    public void restockBatchCalculatesAndOrdersLowStockAlerts() {
        List<StockItem> items = List.of(
                new StockItem("SKU-17", 4, 12),
                new StockItem("SKU-03", 2, 8),
                new StockItem("SKU-42", 15, 10),
                new StockItem("SKU-09", 1, 6));

        List<String> alerts = Flowable.fromIterable(items)
                .parallel(2)
                .runOn(Schedulers.computation())
                .map(StockItem::restockAlert)
                .filter(alert -> alert.remainingUnits() < 10)
                .sequential()
                .sorted(Comparator.comparing(StockAlert::sku))
                .map(StockAlert::message)
                .toList()
                .blockingGet();

        assertEquals(List.of(
                "SKU-03: order 6 units",
                "SKU-09: order 5 units",
                "SKU-17: order 8 units"), alerts);
    }

    private record StockItem(String sku, int remainingUnits, int reorderLevel) {
        private StockAlert restockAlert() {
            return new StockAlert(sku, remainingUnits, reorderLevel - remainingUnits);
        }
    }

    private record StockAlert(String sku, int remainingUnits, int unitsToOrder) {
        private String message() {
            return sku + ": order " + unitsToOrder + " units";
        }
    }
}
