/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.io.IOException;

import org.apache.curator.shaded.com.google.common.io.Closer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CloserInnerSuppressingSuppressorTest {
    @Test
    void closeAddsEarlierResourceFailureAsSuppressedException() {
        IOException earlierFailure = new IOException("earlier resource");
        IOException laterFailure = new IOException("later resource");
        Closer closer = Closer.create();
        closer.register(() -> {
            throw earlierFailure;
        });
        closer.register(() -> {
            throw laterFailure;
        });

        assertThatThrownBy(closer::close)
                .isSameAs(laterFailure)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(earlierFailure));
    }
}
