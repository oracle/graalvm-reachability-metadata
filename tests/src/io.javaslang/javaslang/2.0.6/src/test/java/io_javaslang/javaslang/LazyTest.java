/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import javaslang.Lazy;
import org.junit.jupiter.api.Test;

public class LazyTest {

    @Test
    void lazyValCreatesProxyThatEvaluatesAndDelegatesThroughInterfaceMethods() {
        final AtomicInteger evaluations = new AtomicInteger();
        final CharSequence value = Lazy.val(() -> {
            evaluations.incrementAndGet();
            return "lazy-value";
        }, CharSequence.class);

        assertThat(evaluations.get()).isZero();
        assertThat(value.length()).isEqualTo(10);
        assertThat(value.charAt(5)).isEqualTo('v');
        assertThat(value.subSequence(0, 4).toString()).isEqualTo("lazy");
        assertThat(evaluations.get()).isOne();
    }
}
