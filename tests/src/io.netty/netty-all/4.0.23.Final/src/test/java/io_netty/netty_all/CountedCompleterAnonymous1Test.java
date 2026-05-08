/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.chmv8.CountedCompleter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CountedCompleterAnonymous1Test {
    @Test
    void countedCompleterInitializesUnsafeAndUpdatesPendingCount() {
        NoOpCountedCompleter completer = new NoOpCountedCompleter();

        completer.addToPendingCount(2);
        int previousPendingCount = completer.decrementPendingCountUnlessZero();
        boolean updatedPendingCount = completer.compareAndSetPendingCount(1, 0);

        assertThat(previousPendingCount).isEqualTo(2);
        assertThat(updatedPendingCount).isTrue();
        assertThat(completer.getPendingCount()).isZero();
    }

    private static final class NoOpCountedCompleter extends CountedCompleter<Void> {
        @Override
        public void compute() {
            tryComplete();
        }
    }
}
