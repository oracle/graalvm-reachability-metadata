/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_async.asyncutil;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.asyncutil.locks.AsyncReadWriteLock.WriteLockToken;
import com.ibm.asyncutil.locks.AsyncStampedLock;

import org.junit.jupiter.api.Test;

public class PlatformDependentInnerAlternativeHolderTest {
    @Test
    void optimisticReadValidationUsesPlatformDependentFenceProvider() {
        AsyncStampedLock lock = AsyncStampedLock.createFair();

        AsyncStampedLock.Stamp stamp = lock.tryOptimisticRead();

        assertThat(stamp).isNotNull();
        assertThat(stamp.validate()).isTrue();
    }

    @Test
    void optimisticReadValidationFailsAfterWriterAcquiresLock() {
        AsyncStampedLock lock = AsyncStampedLock.createFair();
        AsyncStampedLock.Stamp stamp = lock.tryOptimisticRead();
        assertThat(stamp).isNotNull();

        WriteLockToken writeLockToken = lock.acquireWriteLock().toCompletableFuture().join();
        try {
            assertThat(lock.tryOptimisticRead()).isNull();
            assertThat(stamp.validate()).isFalse();
        } finally {
            writeLockToken.releaseLock();
        }

        AsyncStampedLock.Stamp nextStamp = lock.tryOptimisticRead();
        assertThat(nextStamp).isNotNull();
        assertThat(nextStamp.validate()).isTrue();
    }
}
