/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_pool.commons_pool;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.fail;

/// Bounded polling for conditions that a pool's background timer or another thread brings about.
public final class Await {
    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private Await() {
    }

    public static void until(String description, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting until " + description);
            }
            Thread.sleep(10);
        }
    }
}
