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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Spring bean-expression evaluation to derive fulfillment batch capacity and queue names from deployment settings.
public class FulfillmentExpressionConfigurationTest {

    @Test
    @Timeout(60)
    public void plansDispatchBatchesUsingExpressionsReferencingDeploymentBeans() {
        List<String> orders = List.of("A", "B", "C", "D", "E", "F", "G");
        try (GenericApplicationContext largeWarehouse = warehouse("north", 2, 3);
                GenericApplicationContext smallWarehouse = warehouse("south", 1, 2)) {
            DispatchPlanner largePlanner = largeWarehouse.getBean(DispatchPlanner.class);
            DispatchPlanner smallPlanner = smallWarehouse.getBean(DispatchPlanner.class);

            assertEquals(List.of(
                    new DispatchBatch("dispatch-north", List.of("A", "B", "C", "D", "E", "F")),
                    new DispatchBatch("dispatch-north", List.of("G"))), largePlanner.plan(orders));
            assertEquals(List.of(
                    new DispatchBatch("dispatch-south", List.of("A", "B")),
                    new DispatchBatch("dispatch-south", List.of("C", "D")),
                    new DispatchBatch("dispatch-south", List.of("E", "F")),
                    new DispatchBatch("dispatch-south", List.of("G"))), smallPlanner.plan(orders));
            assertEquals(List.of(), largePlanner.plan(List.of()));
            assertEquals(List.of(new DispatchBatch("dispatch-north", List.of("H", "I"))),
                    largePlanner.plan(List.of("H", "I")));
        }
    }

    private static GenericApplicationContext warehouse(String region, int workers, int ordersPerWorker) {
        GenericApplicationContext context = new GenericApplicationContext();
        try {
            context.registerBean("capacity", Map.class, () -> Map.of(
                    "region", region,
                    "workers", workers,
                    "ordersPerWorker", ordersPerWorker));
            context.registerBeanDefinition("dispatchPlanner", BeanDefinitionBuilder
                    .genericBeanDefinition(DispatchPlanner.class)
                    .addConstructorArgValue("#{@capacity['workers'] * @capacity['ordersPerWorker']}")
                    .addConstructorArgValue("dispatch-#{@capacity['region']}")
                    .getBeanDefinition());
            context.refresh();
            return context;
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    public record DispatchBatch(String queue, List<String> orders) {
    }

    public static final class DispatchPlanner {
        private final int batchCapacity;
        private final String queue;

        public DispatchPlanner(int batchCapacity, String queue) {
            if (batchCapacity < 1) {
                throw new IllegalArgumentException("Dispatch capacity must be positive");
            }
            this.batchCapacity = batchCapacity;
            this.queue = queue;
        }

        public List<DispatchBatch> plan(List<String> orders) {
            List<DispatchBatch> batches = new ArrayList<>();
            for (int start = 0; start < orders.size(); start += batchCapacity) {
                int end = Math.min(start + batchCapacity, orders.size());
                batches.add(new DispatchBatch(queue, List.copyOf(orders.subList(start, end))));
            }
            return List.copyOf(batches);
        }
    }
}
