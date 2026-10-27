/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.diffplug.common.io.Closer;
import java.io.Closeable;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class CloserInnerSuppressingSuppressorTest {
    @Test
    void closeAddsLaterCloseFailureAsSuppressedException() throws IOException {
        IOException primaryFailure = new IOException("primary close failure");
        IOException secondaryFailure = new IOException("secondary close failure");
        Closer closer = Closer.create();
        closer.register(new ThrowingCloseable(secondaryFailure));
        closer.register(new ThrowingCloseable(primaryFailure));

        try {
            closer.close();
            fail("Expected the primary close failure to be thrown");
        } catch (IOException thrown) {
            assertThat(thrown).isSameAs(primaryFailure);
            assertThat(thrown.getSuppressed()).containsExactly(secondaryFailure);
        }
    }

    private static final class ThrowingCloseable implements Closeable {
        private final IOException failure;

        private ThrowingCloseable(IOException failure) {
            this.failure = failure;
        }

        @Override
        public void close() throws IOException {
            throw failure;
        }
    }
}
