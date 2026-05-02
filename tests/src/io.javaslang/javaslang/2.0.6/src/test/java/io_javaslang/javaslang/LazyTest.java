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
    public void valCreatesMemoizedProxyForInterfaceCalls() {
        final AtomicInteger evaluations = new AtomicInteger();
        final CharSequence text = Lazy.val(() -> {
            evaluations.incrementAndGet();
            return "expanded-text";
        }, CharSequence.class);

        assertThat(evaluations).hasValue(0);
        assertThat(text.length()).isEqualTo(13);
        assertThat(evaluations).hasValue(1);
        assertThat(text.charAt(9)).isEqualTo('t');
        assertThat(text.subSequence(0, 8).toString()).isEqualTo("expanded");
        assertThat(text.toString()).isEqualTo("expanded-text");
        assertThat(evaluations).hasValue(1);
    }
}
