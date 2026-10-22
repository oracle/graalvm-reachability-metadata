/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Callback;
import com.sun.jna.IntegerType;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.ref.Reference;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Exercises JNA's native callbacks to order a job queue through C qsort using application priorities.
 */
public class NativeJobQueueSortTest {

    @Test
    @Timeout(60)
    public void sortsJobsByPriorityAndThenIdentifier() {
        NativeSort sorter = Native.load(Platform.C_LIBRARY_NAME, NativeSort.class);
        Map<Integer, Integer> priorities = Map.of(101, 1, 102, 2, 103, 1, 104, 3, 105, 3);
        int[] jobs = {104, 102, 105, 103, 101};
        JobComparator comparator = (left, right) -> {
            int leftId = left.getInt(0);
            int rightId = right.getInt(0);
            int priorityOrder = Integer.compare(priorities.get(leftId), priorities.get(rightId));
            return priorityOrder == 0 ? Integer.compare(leftId, rightId) : priorityOrder;
        };

        try {
            // JNA copies the array back after qsort calls the Java comparator with native pointers.
            sorter.qsort(jobs, new SizeT(jobs.length), new SizeT(Integer.BYTES), comparator);
            assertArrayEquals(new int[]{101, 103, 102, 104, 105}, jobs,
                    "Urgent jobs must lead the queue, with deterministic ordering within each priority");
        } finally {
            // Native callback registrations do not keep their Java callbacks strongly reachable.
            Reference.reachabilityFence(comparator);
        }
    }

    public interface NativeSort extends Library {
        void qsort(int[] jobs, SizeT count, SizeT elementSize, JobComparator comparator);
    }

    public interface JobComparator extends Callback {
        int compare(Pointer left, Pointer right);
    }

    // size_t follows pointer width, including platforms where C long is narrower than a pointer.
    public static class SizeT extends IntegerType {
        public SizeT() {
            this(0);
        }

        public SizeT(long value) {
            super(Native.SIZE_T_SIZE, value, true);
        }
    }
}
