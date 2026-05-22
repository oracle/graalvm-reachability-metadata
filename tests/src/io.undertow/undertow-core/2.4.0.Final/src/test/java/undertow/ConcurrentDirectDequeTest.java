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
    void newInstanceCreatesUsableDeque() {
        ConcurrentDirectDeque<String> deque = ConcurrentDirectDeque.newInstance();

        Object firstToken = deque.offerFirstAndReturnToken("first");
        Object secondToken = deque.offerLastAndReturnToken("second");

        assertThat(deque).containsExactly("first", "second");
        deque.removeToken(firstToken);
        assertThat(deque).containsExactly("second");
        deque.removeToken(secondToken);
        assertThat(deque).isEmpty();
    }
}
