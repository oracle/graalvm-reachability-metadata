/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.lang.caller.StackTraceCaller;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StackTraceCallerTest {
    @Test
    void returnsCallerFromStackTrace() {
        Class<?> caller = CallerLevel.resolveCaller();

        assertThat(caller).isEqualTo(StackTraceCallerTest.class);
    }

    @Test
    void returnsCallerCallerFromStackTrace() {
        Class<?> callerCaller = FirstCallerCallerLevel.resolveCallerCaller();

        assertThat(callerCaller).isEqualTo(StackTraceCallerTest.class);
    }

    @Test
    void returnsCallerAtRequestedDepthFromStackTrace() {
        Class<?> caller = DepthCallerLevel.resolveDepthZeroCaller();

        assertThat(caller).isEqualTo(DepthCallerLevel.class);
    }

    static class CallerLevel {
        static Class<?> resolveCaller() {
            return new StackTraceCaller().getCaller();
        }
    }

    static class FirstCallerCallerLevel {
        static Class<?> resolveCallerCaller() {
            return SecondCallerCallerLevel.resolveCallerCaller();
        }
    }

    static class SecondCallerCallerLevel {
        static Class<?> resolveCallerCaller() {
            return new StackTraceCaller().getCallerCaller();
        }
    }

    static class DepthCallerLevel {
        static Class<?> resolveDepthZeroCaller() {
            return new StackTraceCaller().getCaller(0);
        }
    }
}
