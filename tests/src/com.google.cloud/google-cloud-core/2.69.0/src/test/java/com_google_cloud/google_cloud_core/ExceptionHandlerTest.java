/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_core;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.cloud.ExceptionHandler;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

public class ExceptionHandlerTest {

    @Test
    void verifyCallerAcceptsCallableWhenDeclaredExceptionIsCovered() {
        ExceptionHandler handler = ExceptionHandler.newBuilder().retryOn(IOException.class).build();
        Callable<String> callable = new IOExceptionCallable();

        assertThatCode(() -> invokeVerifyCaller(handler, callable)).doesNotThrowAnyException();
    }

    private static void invokeVerifyCaller(ExceptionHandler handler, Callable<?> callable) throws Throwable {
        MethodHandle verifyCaller = MethodHandles.privateLookupIn(
            ExceptionHandler.class,
            MethodHandles.lookup())
            .findVirtual(
                ExceptionHandler.class,
                "verifyCaller",
                MethodType.methodType(void.class, Callable.class));
        verifyCaller.invoke(handler, callable);
    }

    private static final class IOExceptionCallable implements Callable<String> {

        @Override
        public String call() throws IOException {
            return "covered";
        }
    }
}
