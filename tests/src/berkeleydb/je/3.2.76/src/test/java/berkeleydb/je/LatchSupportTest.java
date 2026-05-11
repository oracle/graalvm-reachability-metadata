/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.latch.Latch;
import com.sleepycat.je.latch.LatchStats;
import com.sleepycat.je.latch.LatchSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LatchSupportTest {

    @Test
    void makeUnnamedLatchConstructsJava5LatchReflectively() throws Exception {
        Class<?> java5LatchClass = LatchSupport.getJava5LatchClass();
        assertThat(java5LatchClass).isNotNull();

        Latch latch = LatchSupport.makeLatch((EnvironmentImpl) null);

        assertThat(latch).isInstanceOf(java5LatchClass);
        assertThat(latch.isOwner()).isFalse();
        assertThat(latch.owner()).isNull();
        assertThat(latch.nWaiters()).isZero();

        assertThat(latch.acquireNoWait()).isTrue();
        try {
            assertThat(latch.isOwner()).isTrue();
            assertThat(latch.owner()).isSameAs(Thread.currentThread());
        } finally {
            latch.release();
        }

        assertThat(latch.isOwner()).isFalse();
        assertThat(latch.owner()).isNull();
        LatchStats stats = latch.getLatchStats();
        assertThat(stats.nAcquireNoWaitSuccessful).isEqualTo(1);
        assertThat(stats.nReleases).isEqualTo(1);
    }
}
