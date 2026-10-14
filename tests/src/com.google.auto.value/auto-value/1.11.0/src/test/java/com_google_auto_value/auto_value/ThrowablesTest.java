/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.base.Throwables;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ThrowablesTest {
    @Test
    void exposesAThrowableStackTraceAsAList() {
        IllegalStateException failure = new IllegalStateException("expected");

        List<StackTraceElement> stackTrace = Throwables.lazyStackTrace(failure);

        assertThat(stackTrace.size()).isEqualTo(failure.getStackTrace().length);
        assertThat(stackTrace.get(0)).isEqualTo(failure.getStackTrace()[0]);
    }
}
