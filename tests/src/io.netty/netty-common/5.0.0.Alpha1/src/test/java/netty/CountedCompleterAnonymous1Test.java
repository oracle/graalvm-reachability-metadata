/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.util.internal.chmv8.CountedCompleter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CountedCompleterAnonymous1Test {
    @Test
    public void pendingCountOperationsUseInitializedUnsafeSupport() {
        PendingTask task = new PendingTask();

        assertThat(task.getPendingCount()).isZero();
        task.addToPendingCount(2);
        assertThat(task.getPendingCount()).isEqualTo(2);
        assertThat(task.compareAndSetPendingCount(2, 1)).isTrue();
        assertThat(task.decrementPendingCountUnlessZero()).isEqualTo(1);
        assertThat(task.getPendingCount()).isZero();
    }

    public static final class PendingTask extends CountedCompleter<Integer> {
        @Override
        public void compute() {
            complete(1);
        }
    }
}
