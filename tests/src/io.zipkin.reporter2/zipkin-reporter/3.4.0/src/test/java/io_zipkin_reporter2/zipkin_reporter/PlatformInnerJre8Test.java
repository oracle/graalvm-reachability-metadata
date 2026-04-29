/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import org.junit.jupiter.api.Test;
import zipkin2.reporter.internal.Platform;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformInnerJre8Test {

    @Test
    void uncheckedIOExceptionCreatesJreUncheckedIOException() {
        IOException cause = new IOException("reporting failed");

        RuntimeException exception = Platform.get().uncheckedIOException(cause);

        assertThat(exception)
                .isInstanceOf(UncheckedIOException.class)
                .hasCause(cause);
    }
}
