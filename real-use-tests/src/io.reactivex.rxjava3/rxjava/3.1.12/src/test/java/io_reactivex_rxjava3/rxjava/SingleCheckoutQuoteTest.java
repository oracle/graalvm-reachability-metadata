/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Single and Maybe composition calculates the checkout quote shown to a customer. */
public class SingleCheckoutQuoteTest {
    @Test
    @Timeout(60)
    public void quoteCombinesPricesAndOptionalLoyaltyCredit() {
        Single<Integer> itemPrice = Single.fromCallable(() -> 2400);
        Single<Integer> deliveryCharge = Single.just(500);
        Maybe<Integer> loyaltyCredit = Maybe.fromOptional(Optional.of(300));

        Single<CheckoutQuote> quote = Single.zip(
                        itemPrice,
                        deliveryCharge,
                        loyaltyCredit.defaultIfEmpty(0),
                        (Integer item, Integer delivery, Integer credit) ->
                                new CheckoutQuote(item, delivery, credit))
                .map(CheckoutQuote::withTotal)
                .cache();

        assertEquals(new CheckoutQuote(2400, 500, 300, 2600), quote.blockingGet());
        assertEquals(new CheckoutQuote(2400, 500, 300, 2600), quote.blockingGet());
    }

    private record CheckoutQuote(int itemPrice, int deliveryCharge, int loyaltyCredit, int total) {
        private CheckoutQuote(int itemPrice, int deliveryCharge, int loyaltyCredit) {
            this(itemPrice, deliveryCharge, loyaltyCredit, 0);
        }

        private CheckoutQuote withTotal() {
            return new CheckoutQuote(itemPrice, deliveryCharge, loyaltyCredit,
                    itemPrice + deliveryCharge - loyaltyCredit);
        }
    }
}
