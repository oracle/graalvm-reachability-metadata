/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.hadoop.thirdparty.com.google.common.base.Throwables;
import org.junit.jupiter.api.Test;

public class ThrowablesTest {
    @Test
    void lazyStackTraceExposesThrowableStackFrames() {
        Throwable throwable = createThrowable();
        StackTraceElement[] expectedStackTrace = throwable.getStackTrace();

        List<StackTraceElement> lazyStackTrace = Throwables.lazyStackTrace(throwable);

        assertThat(lazyStackTrace).containsExactly(expectedStackTrace);
        assertThat(lazyStackTrace.size()).isEqualTo(expectedStackTrace.length);
        assertThat(lazyStackTrace.get(0)).isEqualTo(expectedStackTrace[0]);
    }

    private static Throwable createThrowable() {
        return new IllegalStateException("stack trace for Throwables.lazyStackTrace");
    }
}
