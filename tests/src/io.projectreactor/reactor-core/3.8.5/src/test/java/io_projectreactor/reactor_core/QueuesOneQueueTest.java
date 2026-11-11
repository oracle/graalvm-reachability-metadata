/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.util.Queue;

import org.junit.jupiter.api.Test;
import reactor.util.concurrent.Queues;

import static org.assertj.core.api.Assertions.assertThat;

public class QueuesOneQueueTest {

    @Test
    void typedToArrayAllocatesArrayForSingleElementQueue() {
        Queue<String> queue = Queues.<String>one().get();
        String[] emptyArray = new String[0];

        assertThat(queue.offer("value")).isTrue();

        String[] values = queue.toArray(emptyArray);

        assertThat(values)
                .isNotSameAs(emptyArray)
                .containsExactly("value");
    }
}
