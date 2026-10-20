/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Spring task execution to export invoices with isolated tenant context on a reusable background worker.
public class TenantInvoiceExecutorTest {

    @Test
    @Timeout(60)
    public void propagatesRequestTenantWithoutLeakingItToSubsequentExports() throws Exception {
        ThreadLocal<String> currentTenant = new ThreadLocal<>();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("invoice-export-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setTaskDecorator(task -> {
            String requestTenant = currentTenant.get();
            return () -> {
                try {
                    if (requestTenant == null) {
                        currentTenant.remove();
                    } else {
                        currentTenant.set(requestTenant);
                    }
                    task.run();
                } finally {
                    currentTenant.remove();
                }
            };
        });
        executor.initialize();

        try {
            InvoiceExporter exporter = new InvoiceExporter(executor, currentTenant);
            currentTenant.set("north-shop");
            Future<String> northernInvoice = exporter.export("order-42", new BigDecimal("12.50"), 3);
            currentTenant.set("south-shop");
            Future<String> southernInvoice = exporter.export("order-43", new BigDecimal("8.00"), 2);
            currentTenant.remove();

            assertEquals("north-shop/order-42: 37.50", northernInvoice.get(10, TimeUnit.SECONDS));
            assertEquals("south-shop/order-43: 16.00", southernInvoice.get(10, TimeUnit.SECONDS));

            // An unscoped request must not inherit the last tenant handled by this worker.
            Future<String> unscopedInvoice = exporter.export("order-44", BigDecimal.ONE, 1);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> unscopedInvoice.get(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertEquals("A tenant is required to export an invoice", failure.getCause().getMessage());

            // A failed export does not prevent the worker from processing the next request.
            currentTenant.set("north-shop");
            Future<String> nextInvoice = exporter.export("order-45", new BigDecimal("4.25"), 2);
            assertEquals("north-shop/order-45: 8.50", nextInvoice.get(10, TimeUnit.SECONDS));
            assertEquals("north-shop", currentTenant.get());
        } finally {
            currentTenant.remove();
            executor.shutdown();
        }
    }

    public static final class InvoiceExporter {
        private final ThreadPoolTaskExecutor executor;
        private final ThreadLocal<String> currentTenant;

        public InvoiceExporter(ThreadPoolTaskExecutor executor, ThreadLocal<String> currentTenant) {
            this.executor = executor;
            this.currentTenant = currentTenant;
        }

        public Future<String> export(String orderId, BigDecimal unitPrice, int quantity) {
            return executor.submit(() -> {
                String tenant = currentTenant.get();
                if (tenant == null) {
                    throw new IllegalStateException("A tenant is required to export an invoice");
                }
                BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
                return tenant + "/" + orderId + ": " + total.toPlainString();
            });
        }
    }
}
