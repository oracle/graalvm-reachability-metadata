/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diffplug.common.io.Closer;
import java.io.Closeable;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class CloserInnerSuppressingSuppressorTest {
    @Test
    void closeAddsSubsequentCloseFailureAsSuppressed() {
        final IOException firstFailure = new IOException("first close failure");
        final IOException secondFailure = new IOException("second close failure");
        final Closer closer = Closer.create();
        closer.register(failingCloseable(secondFailure));
        closer.register(failingCloseable(firstFailure));

        assertThatThrownBy(closer::close)
                .isSameAs(firstFailure)
                .satisfies(throwable -> assertThat(throwable.getSuppressed()).containsExactly(secondFailure));
    }

    private static Closeable failingCloseable(IOException failure) {
        return () -> {
            throw failure;
        };
    }
}
