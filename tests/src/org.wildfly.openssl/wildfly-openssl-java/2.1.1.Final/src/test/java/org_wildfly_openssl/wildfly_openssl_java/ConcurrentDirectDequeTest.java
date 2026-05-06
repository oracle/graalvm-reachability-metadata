/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_openssl.wildfly_openssl_java;

import org.junit.jupiter.api.Test;
import org.wildfly.openssl.util.ConcurrentDirectDeque;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentDirectDequeTest {
    @Test
    void newInstanceCreatesUsableDequeWithTokenRemoval() {
        ConcurrentDirectDeque<String> deque = ConcurrentDirectDeque.newInstance();

        Object firstToken = deque.offerFirstAndReturnToken("first");
        Object lastToken = deque.offerLastAndReturnToken("last");

        assertThat(firstToken).isNotNull();
        assertThat(lastToken).isNotNull();
        assertThat(deque).containsExactly("first", "last");

        deque.removeToken(firstToken);
        assertThat(deque).containsExactly("last");
        assertThat(deque.pollFirst()).isEqualTo("last");
        assertThat(deque).isEmpty();
    }
}
