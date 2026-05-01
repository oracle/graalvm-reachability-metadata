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
    private final StackTraceCaller caller = new StackTraceCaller();

    @Test
    public void resolvesCallerFromStackTrace() {
        Class<?> resolvedCaller = captureCaller();

        assertThat(resolvedCaller).isEqualTo(StackTraceCallerTest.class);
    }

    @Test
    public void resolvesCallerCallerFromStackTrace() {
        Class<?> resolvedCallerCaller = firstCallerCallerLevel();

        assertThat(resolvedCallerCaller).isEqualTo(StackTraceCallerTest.class);
    }

    @Test
    public void resolvesCallerAtRequestedDepthFromStackTrace() {
        Class<?> resolvedCaller = firstDepthLevel();

        assertThat(resolvedCaller).isEqualTo(StackTraceCallerTest.class);
    }

    private Class<?> captureCaller() {
        return caller.getCaller();
    }

    private Class<?> firstCallerCallerLevel() {
        return captureCallerCaller();
    }

    private Class<?> captureCallerCaller() {
        return caller.getCallerCaller();
    }

    private Class<?> firstDepthLevel() {
        return captureCallerAtDepth();
    }

    private Class<?> captureCallerAtDepth() {
        return caller.getCaller(2);
    }
}
