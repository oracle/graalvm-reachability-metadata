/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.core.Observable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Observable operators build the shopping cart summaries shown by a customer application. */
public class ObservableShoppingCartSummaryTest {
    @Test
    @Timeout(60)
    public void cartUpdatesProduceVisibleSummariesInOrder() {
        Observable<CartEvent> updates = Observable.fromIterable(List.of(
                new CartEvent("coffee", 2, 450),
                new CartEvent("tea", 1, 300),
                new CartEvent("coffee", 1, 450),
                new CartEvent("tea", -1, 300)));

        List<String> summaries = updates
                .scan(CartState.empty(), CartState::apply)
                .map(CartState::summary)
                .toList()
                .blockingGet();

        assertEquals(List.of("0 items / 0 cents", "2 items / 900 cents", "3 items / 1200 cents",
                "4 items / 1650 cents", "3 items / 1350 cents"), summaries);
    }

    private record CartEvent(String sku, int quantityDelta, int unitPriceCents) {
    }

    private record CartState(int itemCount, int totalCents) {
        private static CartState empty() {
            return new CartState(0, 0);
        }

        private CartState apply(CartEvent event) {
            return new CartState(itemCount + event.quantityDelta(),
                    totalCents + event.quantityDelta() * event.unitPriceCents());
        }

        private String summary() {
            return itemCount + " items / " + totalCents + " cents";
        }
    }
}
