/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_core;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.cloud.ExceptionHandler;
import com.google.cloud.ExceptionHandlerAccess;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

public class ExceptionHandlerTest {
    @Test
    void verifiesCallableDeclaredCheckedExceptionIsCovered() {
        ExceptionHandler handler = ExceptionHandler.newBuilder().retryOn(IOException.class).build();

        assertThatCode(
                        () ->
                                ExceptionHandlerAccess.verifyCaller(
                                        handler, new IOExceptionCallable()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCallableDeclaredCheckedExceptionWhenNotCovered() {
        ExceptionHandler handler =
                ExceptionHandler.newBuilder().abortOn(RuntimeException.class).build();

        assertThatThrownBy(
                        () ->
                                ExceptionHandlerAccess.verifyCaller(
                                        handler, new IOExceptionCallable()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Declared exception")
                .hasMessageContaining(IOException.class.getName());
    }

    public static class IOExceptionCallable implements Callable<Object> {
        @Override
        public Object call() throws IOException {
            return "ok";
        }
    }
}
