/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.SimpleThreadScope;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Spring thread-scoped beans to isolate batch-import totals and clear them before reusing a worker.
public class WorkerScopedImportTest {

    @Test
    @Timeout(60)
    public void retainsEachWorkersTotalsAcrossRowsWithoutLeakingIntoTheNextBatch() throws Exception {
        SimpleThreadScope scope = new SimpleThreadScope();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.getBeanFactory().registerScope("importWorker", scope);
            context.registerBean("importTotals", ImportTotals.class, ImportTotals::new,
                    definition -> definition.setScope("importWorker"));
            context.refresh();
            BatchImporter importer = new BatchImporter(context.getBeanProvider(ImportTotals.class), scope);

            try (ExecutorService north = Executors.newSingleThreadExecutor();
                    ExecutorService south = Executors.newSingleThreadExecutor()) {
                // Each row is a separate task, but its worker's scoped accumulator must survive.
                north.submit(() -> importer.add("coffee", 2, new BigDecimal("12.50")))
                        .get(10, TimeUnit.SECONDS);
                south.submit(() -> importer.add("cocoa", 3, new BigDecimal("4.00")))
                        .get(10, TimeUnit.SECONDS);
                north.submit(() -> importer.add("tea", 1, new BigDecimal("8.00")))
                        .get(10, TimeUnit.SECONDS);
                south.submit(() -> importer.add("sugar", 2, new BigDecimal("1.25")))
                        .get(10, TimeUnit.SECONDS);

                ImportReport northReport = north.submit(() -> importer.finish("north-1"))
                        .get(10, TimeUnit.SECONDS);
                assertEquals(new ImportReport("north-1", List.of("coffee", "tea"), 3,
                        new BigDecimal("33.00")), northReport);

                // Finishing one worker's batch must not remove the other worker's accumulator.
                ImportReport southReport = south.submit(() -> importer.finish("south-1"))
                        .get(10, TimeUnit.SECONDS);
                assertEquals(new ImportReport("south-1", List.of("cocoa", "sugar"), 5,
                        new BigDecimal("14.50")), southReport);

                north.submit(() -> importer.add("biscuits", 4, new BigDecimal("2.25")))
                        .get(10, TimeUnit.SECONDS);
                assertEquals(new ImportReport("north-2", List.of("biscuits"), 4, new BigDecimal("9.00")),
                        north.submit(() -> importer.finish("north-2")).get(10, TimeUnit.SECONDS));
                assertEquals(new ImportReport("south-2", List.of(), 0, new BigDecimal("0.00")),
                        south.submit(() -> importer.finish("south-2")).get(10, TimeUnit.SECONDS));
            }
        }
    }

    public record ImportReport(String batch, List<String> products, int units, BigDecimal total) {
    }

    public static final class BatchImporter {
        private final ObjectProvider<ImportTotals> totals;
        private final SimpleThreadScope scope;

        public BatchImporter(ObjectProvider<ImportTotals> totals, SimpleThreadScope scope) {
            this.totals = totals;
            this.scope = scope;
        }

        public void add(String product, int quantity, BigDecimal unitPrice) {
            totals.getObject().add(product, quantity, unitPrice);
        }

        public ImportReport finish(String batch) {
            try {
                return totals.getObject().report(batch);
            } finally {
                // Thread scope has no automatic batch boundary; remove it on its owning worker.
                scope.remove("importTotals");
            }
        }
    }

    public static final class ImportTotals {
        private final List<String> products = new ArrayList<>();
        private int units;
        private BigDecimal total = new BigDecimal("0.00");

        public void add(String product, int quantity, BigDecimal unitPrice) {
            products.add(product);
            units += quantity;
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }

        public ImportReport report(String batch) {
            return new ImportReport(batch, List.copyOf(products), units, total);
        }
    }
}
