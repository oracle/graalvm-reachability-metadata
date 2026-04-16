/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v2.lang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadUtilsTest {
    @Test
    void reflectiveHoldsLockMatchesTheCurrentThreadMonitorState() {
        Object monitor = new Object();

        synchronized (monitor) {
            assertThat(ThreadUtils.reflectiveHoldsLock(monitor)).isTrue();
        }

        assertThat(ThreadUtils.reflectiveHoldsLock(monitor)).isFalse();
    }
}
