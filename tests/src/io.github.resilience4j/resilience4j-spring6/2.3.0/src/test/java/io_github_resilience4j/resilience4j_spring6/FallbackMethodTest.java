/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_spring6;

import io.github.resilience4j.spring6.fallback.FallbackMethod;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class FallbackMethodTest {

    @Test
    void invokesThrowableOnlyFallbackWhenOriginalMethodHasArguments() throws Throwable {
        ThrowableOnlyWithArgsService service = new ThrowableOnlyWithArgsService();
        Method originalMethod = ThrowableOnlyWithArgsService.class.getMethod("call", String.class);
        FallbackMethod fallbackMethod = FallbackMethod.create(
            "recover",
            originalMethod,
            new Object[]{"alpha"},
            service,
            service
        );

        Object result = fallbackMethod.fallback(new IllegalArgumentException("not used"));

        assertThat(result).isEqualTo("throwable-only:IllegalArgumentException");
        assertThat(fallbackMethod.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void invokesFallbackWithOriginalArgumentsAndThrowable() throws Throwable {
        ArgsAndThrowableService service = new ArgsAndThrowableService();
        Method originalMethod = ArgsAndThrowableService.class.getMethod("call", String.class);
        FallbackMethod fallbackMethod = FallbackMethod.create(
            "recover",
            originalMethod,
            new Object[]{"bravo"},
            service,
            service
        );

        Object result = fallbackMethod.fallback(new IllegalArgumentException("not used"));

        assertThat(result).isEqualTo("args-and-throwable:bravo:IllegalArgumentException");
    }

    @Test
    void invokesFallbackWhenOriginalMethodHasNoArguments() throws Throwable {
        NoArgsService service = new NoArgsService();
        Method originalMethod = NoArgsService.class.getMethod("call");
        FallbackMethod fallbackMethod = FallbackMethod.create(
            "recover",
            originalMethod,
            new Object[0],
            service,
            service
        );

        Object result = fallbackMethod.fallback(new IllegalArgumentException("not used"));

        assertThat(result).isEqualTo("no-args:IllegalArgumentException");
    }

    public static class ThrowableOnlyWithArgsService {

        public String call(String input) {
            return input;
        }

        public String recover(Throwable throwable) {
            return "throwable-only:" + throwable.getClass().getSimpleName();
        }
    }

    public static class ArgsAndThrowableService {

        public String call(String input) {
            return input;
        }

        public String recover(String input, IllegalArgumentException throwable) {
            return "args-and-throwable:" + input + ":" + throwable.getClass().getSimpleName();
        }
    }

    public static class NoArgsService {

        public String call() {
            return "primary";
        }

        public String recover(IllegalArgumentException throwable) {
            return "no-args:" + throwable.getClass().getSimpleName();
        }
    }
}
