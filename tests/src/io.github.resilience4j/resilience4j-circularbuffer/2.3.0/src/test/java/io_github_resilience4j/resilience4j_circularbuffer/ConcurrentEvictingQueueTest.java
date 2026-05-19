/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_circularbuffer;

import io.github.resilience4j.circularbuffer.ConcurrentEvictingQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentEvictingQueueTest {

    @Test
    void typedToArrayAllocatesRuntimeTypedArrayWhenDestinationIsTooSmall() {
        ConcurrentEvictingQueue<String> queue = new ConcurrentEvictingQueue<>(3);
        queue.offer("first");
        queue.offer("second");
        queue.offer("third");
        queue.offer("fourth");

        String[] destination = new String[1];
        String[] snapshot = queue.toArray(destination);

        assertThat(snapshot)
                .isNotSameAs(destination)
                .isInstanceOf(String[].class)
                .containsExactly("second", "third", "fourth");
    }
}
