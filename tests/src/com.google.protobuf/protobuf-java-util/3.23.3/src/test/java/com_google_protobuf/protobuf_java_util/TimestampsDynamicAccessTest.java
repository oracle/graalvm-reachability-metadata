/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public final class TimestampsDynamicAccessTest {
    @Test
    public void nowUsesAvailableSystemClock() {
        Timestamp lowerBound = Timestamps.fromMillis(System.currentTimeMillis() - 5000L);
        Timestamp timestamp = Timestamps.now();
        Timestamp upperBound = Timestamps.fromMillis(System.currentTimeMillis() + 5000L);

        assertThat(Timestamps.isValid(timestamp)).isTrue();
        assertThat(Timestamps.compare(timestamp, lowerBound)).isAtLeast(0);
        assertThat(Timestamps.compare(timestamp, upperBound)).isAtMost(0);
    }
}
