/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.ArrayBlockingQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayBlockingQueueTest {
    @Test
    void returnsNewTypedArrayWhenDestinationIsTooSmall() throws InterruptedException {
        ArrayBlockingQueue queue = new ArrayBlockingQueue(4);
        queue.put("first");
        queue.put("second");
        queue.put("third");
        assertThat(queue.take()).isEqualTo("first");
        queue.put("fourth");
        queue.put("fifth");

        String[] contents = (String[]) queue.toArray(new String[0]);

        assertThat(contents).containsExactly("second", "third", "fourth", "fifth");
        assertThat(contents).isInstanceOf(String[].class);
    }

    @Test
    void reusesTypedArrayWhenDestinationHasEnoughRoom() {
        ArrayBlockingQueue queue = new ArrayBlockingQueue(3);
        queue.add("alpha");
        queue.add("beta");
        String[] destination = new String[] {"stale", "stale", "sentinel"};

        Object[] result = queue.toArray(destination);

        assertThat(result).isSameAs(destination);
        assertThat(destination).containsExactly("alpha", "beta", null);
    }
}
