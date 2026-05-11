/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.latch.SharedLatchImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SharedLatchImplTest {

    @Test
    void sharedAndExclusiveAcquisitionTracksCurrentThreadOwnership() throws Exception {
        SharedLatchImpl latch = new SharedLatchImpl("shared-coverage-latch", null);

        assertThat(latch.isOwner()).isFalse();
        assertThat(latch.isWriteLockedByCurrentThread()).isFalse();

        latch.acquireShared();
        assertThat(latch.isOwner()).isTrue();
        assertThat(latch.isWriteLockedByCurrentThread()).isFalse();

        latch.release();
        assertThat(latch.isOwner()).isFalse();

        assertThat(latch.acquireExclusiveNoWait()).isTrue();
        assertThat(latch.isOwner()).isTrue();
        assertThat(latch.isWriteLockedByCurrentThread()).isTrue();

        latch.releaseIfOwner();
        assertThat(latch.isOwner()).isFalse();
        assertThat(latch.isWriteLockedByCurrentThread()).isFalse();
    }
}
