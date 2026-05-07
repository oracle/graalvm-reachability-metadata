/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.util.ConcurrentDirectDeque;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentDirectDequeTest {

    @Test
    void createsDequeThroughFactoryAndSupportsDirectTokenRemoval() {
        ConcurrentDirectDeque<String> deque = ConcurrentDirectDeque.newInstance();

        Object middleToken = deque.offerLastAndReturnToken("bravo");
        deque.offerFirstAndReturnToken("alpha");
        deque.offerLast("charlie");

        assertThat(deque).containsExactly("alpha", "bravo", "charlie");

        deque.removeToken(middleToken);

        assertThat(deque).containsExactly("alpha", "charlie");
        assertThat(deque.pollFirst()).isEqualTo("alpha");
        assertThat(deque.pollLast()).isEqualTo("charlie");
        assertThat(deque).isEmpty();
    }
}
