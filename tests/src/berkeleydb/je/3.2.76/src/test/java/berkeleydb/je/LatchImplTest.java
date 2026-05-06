/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.latch.LatchImpl;
import com.sleepycat.je.latch.LatchStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LatchImplTest {

    @Test
    void acquireNoWaitOwnsAndReleasesLatch() throws Exception {
        LatchImpl latch = new LatchImpl("coverage-latch", null);

        assertThat(latch.isOwner()).isFalse();
        assertThat(latch.owner()).isNull();
        assertThat(latch.nWaiters()).isZero();

        assertThat(latch.acquireNoWait()).isTrue();
        assertThat(latch.isOwner()).isTrue();
        assertThat(latch.owner()).isSameAs(Thread.currentThread());

        latch.release();

        assertThat(latch.isOwner()).isFalse();
        assertThat(latch.owner()).isNull();
        LatchStats stats = latch.getLatchStats();
        assertThat(stats.nAcquireNoWaitSuccessful).isEqualTo(1);
        assertThat(stats.nReleases).isEqualTo(1);
    }
}
