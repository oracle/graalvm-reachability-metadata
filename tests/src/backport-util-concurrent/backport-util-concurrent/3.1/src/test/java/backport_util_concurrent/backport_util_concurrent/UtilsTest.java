/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.helpers.NanoTimer;
import edu.emory.mathcs.backport.java.util.concurrent.helpers.Utils;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest implements NanoTimer {
    private static final long STEP_NANOS = 37L;
    private static final String NANO_TIMER_PROVIDER_PROPERTY =
            "edu.emory.mathcs.backport.java.util.concurrent.NanoTimerProvider";
    private static final String NANO_TIMER_PROVIDER_CLASS =
            "backport_util_concurrent.backport_util_concurrent.UtilsTest";

    private long currentValue = 1_000_000L;

    static {
        System.setProperty(NANO_TIMER_PROVIDER_PROPERTY, NANO_TIMER_PROVIDER_CLASS);
    }

    @Test
    void nanoTimeUsesConfiguredProvider() {
        long firstValue = Utils.nanoTime();
        long secondValue = Utils.nanoTime();

        assertThat(secondValue - firstValue).isEqualTo(STEP_NANOS);
    }

    @Test
    void collectionToArrayTrimsWhenReportedSizeIsLargerThanIteration() {
        Object[] values = Utils.collectionToArray(new SizeReportingCollection(4, "alpha", "bravo"));

        assertThat(values).containsExactly("alpha", "bravo");
        assertThat(values.getClass()).isEqualTo(Object[].class);
    }

    @Test
    void collectionToTypedArrayAllocatesComponentTypeWhenSuppliedArrayIsTooSmall() {
        String[] values = (String[]) Utils.collectionToArray(
                Arrays.asList(new String[] {"alpha", "bravo", "charlie"}),
                new String[0]);

        assertThat(values).containsExactly("alpha", "bravo", "charlie");
        assertThat(values.getClass()).isEqualTo(String[].class);
    }

    @Override
    public long nanoTime() {
        long returnedValue = currentValue;
        currentValue += STEP_NANOS;
        return returnedValue;
    }

    private static final class SizeReportingCollection extends AbstractCollection<Object> {
        private final int reportedSize;
        private final Object[] values;

        private SizeReportingCollection(int reportedSize, Object... values) {
            this.reportedSize = reportedSize;
            this.values = values;
        }

        @Override
        public Iterator<Object> iterator() {
            return Arrays.asList(values).iterator();
        }

        @Override
        public int size() {
            return reportedSize;
        }
    }
}
