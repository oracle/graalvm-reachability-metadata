/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CompletionStage interoperation lets an order service use an RxJava pricing workflow. */
public class CompletionStageOrderPricingTest {
    @Test
    @Timeout(60)
    public void pricingWorkflowAcceptsAndReturnsCompletionStages() throws Exception {
        CompletionStage<OrderRequest> request = CompletableFuture.completedFuture(
                new OrderRequest("ORD-204", 3, 750));

        CompletionStage<PriceQuote> quote = Single.fromCompletionStage(request)
                .map(this::calculateQuote)
                .toCompletionStage();

        assertEquals(new PriceQuote("ORD-204", 2250, 180),
                quote.toCompletableFuture().get(10, TimeUnit.SECONDS));
    }

    private PriceQuote calculateQuote(OrderRequest request) {
        return new PriceQuote(request.orderId(), request.unitPriceCents() * request.quantity(), 180);
    }

    private record OrderRequest(String orderId, int quantity, int unitPriceCents) {
    }

    private record PriceQuote(String orderId, int subtotalCents, int deliveryCents) {
    }
}
