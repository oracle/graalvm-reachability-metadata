/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.io.Closer;

import java.io.Closeable;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CloserInnerSuppressingSuppressorTest {
    @Test
    void retainsFailuresFromEveryRegisteredCloseable() {
        Closer closer = Closer.create();
        closer.register(new FailingCloseable("first"));
        closer.register(new FailingCloseable("second"));

        assertThatThrownBy(closer::close)
            .isInstanceOf(IOException.class)
            .hasMessage("second")
            .satisfies(error -> org.assertj.core.api.Assertions.assertThat(error.getSuppressed()).hasSize(1));
    }

    public static class FailingCloseable implements Closeable {
        private final String message;

        public FailingCloseable(String message) {
            this.message = message;
        }

        @Override
        public void close() throws IOException {
            throw new IOException(message);
        }
    }
}
