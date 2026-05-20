/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_reactive.mutiny;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Queue;
import java.util.function.Supplier;

import io.smallrye.mutiny.helpers.queues.Queues;
import org.junit.jupiter.api.Test;

public class SingletonQueueTest {

    @Test
    public void toArrayExpandsTypedArrayUsingElementComponentType() {
        Supplier<Queue<String>> supplier = Queues.get(1);
        Queue<String> queue = supplier.get();

        assertTrue(queue.offer("mutiny"));

        String[] values = queue.toArray(new String[0]);

        assertArrayEquals(new String[] {"mutiny"}, values);
    }
}
