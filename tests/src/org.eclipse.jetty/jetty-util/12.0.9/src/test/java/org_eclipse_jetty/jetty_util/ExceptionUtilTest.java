/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.io.IOException;

import org.eclipse.jetty.util.ExceptionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionUtilTest {
    @Test
    void convertsAThrowableUsingTheRequestedExceptionConstructor() {
        IllegalStateException cause = new IllegalStateException("original cause");

        IOException converted = ExceptionUtil.as(IOException.class, cause);

        assertThat(converted).hasCause(cause);
    }
}
