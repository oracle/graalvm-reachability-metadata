/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Closeable;
import java.io.IOException;
import org.apache.hadoop.thirdparty.com.google.common.io.Closer;
import org.junit.jupiter.api.Test;

public class CloserInnerSuppressingSuppressorTest {
    @Test
    void closeSuppressesLaterCloseFailuresOnThePrimaryFailure() throws IOException {
        IOException secondaryFailure = new IOException("secondary close failure");
        IOException primaryFailure = new IOException("primary close failure");
        Closer closer = Closer.create();
        closer.register(new ThrowingCloseable(secondaryFailure));
        closer.register(new ThrowingCloseable(primaryFailure));

        IOException thrown = assertThrows(IOException.class, closer::close);

        assertThat(thrown).isSameAs(primaryFailure);
        assertThat(thrown.getSuppressed()).containsExactly(secondaryFailure);
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
