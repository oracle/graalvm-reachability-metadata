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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CloserSuppressingSuppressorTest {
    @Test
    void suppressesASecondaryCloseFailure() {
        Closer closer = Closer.create();
        closer.register(() -> {
            throw new IOException("secondary");
        });
        closer.register(() -> {
            throw new IOException("primary");
        });

        assertThatThrownBy(closer::close)
                .isInstanceOf(IOException.class)
                .hasMessage("primary")
                .hasSuppressedException(new IOException("secondary"));
    }
}
