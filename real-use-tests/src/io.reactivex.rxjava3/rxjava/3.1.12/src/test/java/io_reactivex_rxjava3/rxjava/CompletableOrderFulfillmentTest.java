/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Completable workflows coordinate the steps that fulfill a customer's order. */
public class CompletableOrderFulfillmentTest {
    @Test
    @Timeout(60)
    public void fulfillmentRunsStepsInOrderBeforeEmittingReceipt() {
        List<String> steps = new ArrayList<>();
        Completable fulfillment = Completable.concatArray(
                        step(steps, "validated"),
                        step(steps, "reserved"),
                        step(steps, "charged"))
                .andThen(step(steps, "confirmed"));
        Single<FulfillmentReceipt> receipt = fulfillment
                .andThen(Single.fromCallable(() -> new FulfillmentReceipt("ORD-204", steps.size())));

        FulfillmentReceipt result = receipt.blockingGet();

        assertEquals(List.of("validated", "reserved", "charged", "confirmed"), steps);
        assertEquals(new FulfillmentReceipt("ORD-204", 4), result);
    }

    private static Completable step(List<String> steps, String name) {
        return Completable.fromAction(() -> steps.add(name));
    }

    private record FulfillmentReceipt(String orderId, int completedSteps) {
    }
}
