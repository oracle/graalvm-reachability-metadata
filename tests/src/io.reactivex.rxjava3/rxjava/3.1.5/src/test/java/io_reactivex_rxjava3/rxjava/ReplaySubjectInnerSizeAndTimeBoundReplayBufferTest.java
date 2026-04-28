/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReplaySubjectInnerSizeAndTimeBoundReplayBufferTest {
    @Test
    void typedValuesSnapshotAllocatesArrayForRetainedItems() {
        TestScheduler scheduler = new TestScheduler();
        ReplaySubject<String> subject = ReplaySubject.createWithTimeAndSize(1, TimeUnit.MINUTES, scheduler, 2);

        subject.onNext("alpha");
        subject.onNext("beta");

        String[] values = subject.getValues(new String[0]);

        assertEquals(String[].class, values.getClass());
        assertArrayEquals(new String[] {"alpha", "beta"}, values);
    }
}
