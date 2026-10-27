/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.wildfly.common.lock.ExtendedLock;
import org.wildfly.common.lock.Locks;

public class JDKSpecificAnonymous1Test {
    @Test
    void spinLockAcquiresAndReleasesForCurrentThread() {
        ExtendedLock lock = Locks.spinLock();

        assertThat(lock.isLocked()).isFalse();
        assertThat(lock.isHeldByCurrentThread()).isFalse();

        lock.lock();
        assertThat(lock.isLocked()).isTrue();
        assertThat(lock.isHeldByCurrentThread()).isTrue();

        lock.unlock();
        assertThat(lock.isLocked()).isFalse();
        assertThat(lock.isHeldByCurrentThread()).isFalse();
    }
}
