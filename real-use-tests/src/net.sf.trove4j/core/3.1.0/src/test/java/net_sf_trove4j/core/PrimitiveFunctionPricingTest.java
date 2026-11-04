/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.function.TDoubleFunction;
import gnu.trove.list.array.TDoubleArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/// Exercises Trove's primitive-function callback subsystem to apply a pricing rule to a batch of amounts.
public class PrimitiveFunctionPricingTest {
    @Test
    @Timeout(60)
    public void appliesTaxAndRoundingToQuotedAmounts() {
        TDoubleArrayList quotedAmounts = new TDoubleArrayList(new double[] {12.49, 8.25, 19.99});
        TDoubleFunction taxAndRounding = amount -> Math.round(amount * 1.0825 * 100.0) / 100.0;

        quotedAmounts.transformValues(taxAndRounding);

        assertArrayEquals(new double[] {13.52, 8.93, 21.64}, quotedAmounts.toArray(), 0.0);
    }
}
