/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.base.Throwables;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ThrowablesTest {
    @Test
    void exposesThrowableStackFramesThroughLazyView() {
        Throwable throwable = new IllegalStateException("GraphQL execution failed");
        StackTraceElement[] expected = throwable.getStackTrace();

        List<StackTraceElement> stackTrace = Throwables.lazyStackTrace(throwable);

        assertThat(stackTrace).containsExactly(expected);
        assertThat(stackTrace.size()).isEqualTo(expected.length);
        assertThat(stackTrace.get(0)).isEqualTo(expected[0]);
    }
}
