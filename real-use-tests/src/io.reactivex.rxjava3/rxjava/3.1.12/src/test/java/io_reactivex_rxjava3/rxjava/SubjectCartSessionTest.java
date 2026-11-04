/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Subjects keep a live shopping cart view current for an application user. */
public class SubjectCartSessionTest {
    @Test
    @Timeout(60)
    public void lateCartViewReceivesCurrentStateAndNextUpdate() throws Exception {
        BehaviorSubject<CartState> currentState = BehaviorSubject.createDefault(CartState.empty());
        PublishSubject<CartCommand> commands = PublishSubject.create();
        Disposable stateSubscription = commands
                .scan(CartState.empty(), CartState::apply)
                .subscribe(currentState::onNext);

        commands.onNext(new CartCommand("coffee", 2, 450));
        commands.onNext(new CartCommand("tea", 1, 300));

        List<String> summaries = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch viewUpdates = new CountDownLatch(2);
        Disposable viewSubscription = currentState
                .observeOn(Schedulers.single())
                .subscribe(state -> {
                    summaries.add(state.summary());
                    viewUpdates.countDown();
                }, failure -> {
                    error.set(failure);
                    viewUpdates.countDown();
                });

        try {
            commands.onNext(new CartCommand("coffee", -1, 450));

            assertTrue(viewUpdates.await(10, TimeUnit.SECONDS));
            assertNull(error.get());
            assertEquals(List.of("3 items / 1200 cents", "2 items / 750 cents"), summaries);
        } finally {
            viewSubscription.dispose();
            stateSubscription.dispose();
        }
    }

    private record CartCommand(String sku, int quantityDelta, int unitPriceCents) {
    }

    private record CartState(int itemCount, int totalCents) {
        private static CartState empty() {
            return new CartState(0, 0);
        }

        private CartState apply(CartCommand command) {
            return new CartState(itemCount + command.quantityDelta(),
                    totalCents + command.quantityDelta() * command.unitPriceCents());
        }

        private String summary() {
            return itemCount + " items / " + totalCents + " cents";
        }
    }
}
