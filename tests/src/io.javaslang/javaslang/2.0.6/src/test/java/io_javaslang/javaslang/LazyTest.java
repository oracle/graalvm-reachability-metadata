/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang;

import javaslang.Lazy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyTest {

    @Test
    void valCreatesProxyThatDefersEvaluationAndMemoizesInterfaceCalls() {
        final AtomicInteger supplierCalls = new AtomicInteger();
        final CharSequence sequence = Lazy.val(() -> {
            supplierCalls.incrementAndGet();
            return new StringBuilder("native-ready");
        }, CharSequence.class);

        assertThat(supplierCalls.get()).isEqualTo(0);
        assertThat(sequence.length()).isEqualTo(12);
        assertThat(sequence.charAt(7)).isEqualTo('r');
        assertThat(sequence.subSequence(7, 12).toString()).isEqualTo("ready");
        assertThat(sequence.toString()).isEqualTo("native-ready");
        assertThat(supplierCalls.get()).isEqualTo(1);
    }
}
