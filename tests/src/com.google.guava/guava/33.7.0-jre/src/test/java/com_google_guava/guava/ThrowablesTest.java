/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Throwables;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ThrowablesTest {
    @Test
    void lazyStackTraceUsesPatchedJavaLangAccessWhenAvailable() {
        Throwable throwable = createThrowableWithStackTrace();

        List<StackTraceElement> lazyStackTrace = Throwables.lazyStackTrace(throwable);

        assertThat(lazyStackTrace).hasSize(throwable.getStackTrace().length);
        assertThat(lazyStackTrace.get(0).getMethodName()).isEqualTo("createThrowableWithStackTrace");
        assertThat(lazyStackTrace).containsExactly(throwable.getStackTrace());
    }

    private static Throwable createThrowableWithStackTrace() {
        return new Throwable("stack trace source");
    }
}
