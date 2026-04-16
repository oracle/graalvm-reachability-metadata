/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.immutables.value.internal.$guava$.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Closeable;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class $Closer$SuppressingSuppressorTest {
    @Test
    void addsCloseFailureAsSuppressedExceptionOnPrimaryFailure() throws IOException {
        final IOException primaryFailure = new IOException("primary");
        final IOException closeFailure = new IOException("close");
        final $Closer closer = $Closer.create();
        closer.register(new ThrowingCloseable(closeFailure));

        try {
            closer.rethrow(primaryFailure);
        } catch (IOException expected) {
            assertThat(expected).isSameAs(primaryFailure);
        } finally {
            closer.close();
        }

        assertThat(primaryFailure.getSuppressed()).containsExactly(closeFailure);
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
