/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_codahale_metrics.metrics_core;

import com.codahale.metrics.Counter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Striped64Anonymous1Test {
    @Test
    public void counterUsesStripedAccumulatorBackedByUnsafe() {
        Counter counter = new Counter();

        counter.inc(41);
        counter.inc();
        counter.dec(2);

        assertEquals(40L, counter.getCount());
    }
}
