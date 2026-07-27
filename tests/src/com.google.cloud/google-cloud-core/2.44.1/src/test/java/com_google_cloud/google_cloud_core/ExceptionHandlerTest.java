/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.ExceptionHandler;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ExceptionHandlerTest {
    @Test
    void retriesConfiguredException() {
        ExceptionHandler handler = ExceptionHandler.newBuilder().retryOn(IOException.class).build();

        assertThat(handler.shouldRetry(new IOException("retry"), null)).isTrue();
    }

    @Test
    void abortsConfiguredException() {
        ExceptionHandler handler =
                ExceptionHandler.newBuilder().abortOn(RuntimeException.class).build();

        assertThat(handler.shouldRetry(new RuntimeException("abort"), null)).isFalse();
    }
}
