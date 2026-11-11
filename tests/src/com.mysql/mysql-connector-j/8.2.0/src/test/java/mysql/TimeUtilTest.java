/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.util.TimeUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TimeUtilTest {
    @Test
    void connectorTimerUsesTheMonotonicNanosecondClock() {
        assertThat(TimeUtil.nanoTimeAvailable()).isTrue();
        long before = System.nanoTime();
        long measured = TimeUtil.getCurrentTimeNanosOrMillis();
        long after = System.nanoTime();
        assertThat(measured - before).isGreaterThanOrEqualTo(0);
        assertThat(after - measured).isGreaterThanOrEqualTo(0);
    }
}
