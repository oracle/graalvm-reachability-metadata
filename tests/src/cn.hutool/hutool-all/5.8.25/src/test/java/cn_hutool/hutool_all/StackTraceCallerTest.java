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
    private static final StackTraceCaller CALLER = new StackTraceCaller();

    @Test
    void resolvesDirectCallerClassFromStackTrace() {
        Class<?> callerClass = DirectCallerProbe.resolveCaller();

        assertThat(callerClass).isEqualTo(StackTraceCallerTest.class);
    }

    @Test
    void resolvesCallerCallerClassFromStackTrace() {
        Class<?> callerCallerClass = OuterCallerProbe.resolveCallerCaller();

        assertThat(callerCallerClass).isEqualTo(StackTraceCallerTest.class);
    }

    @Test
    void resolvesCallerClassAtRequestedDepthFromStackTrace() {
        Class<?> immediateCallerClass = DepthProbe.resolveCallerAtDepth(0);
        Class<?> testCallerClass = DepthProbe.resolveCallerAtDepth(1);

        assertThat(immediateCallerClass).isEqualTo(DepthProbe.class);
        assertThat(testCallerClass).isEqualTo(StackTraceCallerTest.class);
    }

    @Test
    void detectsClassPresentInCurrentCallStack() {
        assertThat(DirectCallerProbe.isCalledByTestClass()).isTrue();
    }

    private static final class DirectCallerProbe {
        private static Class<?> resolveCaller() {
            return CALLER.getCaller();
        }

        private static boolean isCalledByTestClass() {
            return CALLER.isCalledBy(StackTraceCallerTest.class);
        }
    }

    private static final class OuterCallerProbe {
        private static Class<?> resolveCallerCaller() {
            return InnerCallerProbe.resolveCallerCaller();
        }
    }

    private static final class InnerCallerProbe {
        private static Class<?> resolveCallerCaller() {
            return CALLER.getCallerCaller();
        }
    }

    private static final class DepthProbe {
        private static Class<?> resolveCallerAtDepth(int depth) {
            return CALLER.getCaller(depth);
        }
    }
}
